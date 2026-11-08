package com.qts.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qts.common.BizException;
import com.qts.entity.ChainTxRecord;
import com.qts.entity.Frame;
import com.qts.entity.InspectionRecord;
import com.qts.mapper.ChainTxRecordMapper;
import com.qts.mapper.FrameMapper;
import com.qts.mapper.InspectionRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 上链存证服务：把车架关键质量数据的 SHA-256 摘要写入联盟链，并回写本地存证状态。
 *
 * <p>事务边界说明（面试常问，也是这里最容易写错的地方）：</p>
 * <ol>
 *   <li><b>先上链、后写库</b>，顺序不可颠倒。链上交易一旦确认无法回滚，如果先写库再上链，
 *       上链失败就会留下「本地标记已上链、链上查不到」的脏数据，溯源时直接报错；
 *       反之若上链成功而本地写库失败，链上那条孤儿记录无害，下次重试即可。</li>
 *   <li><b>本地两步写入必须同事务</b>：更新 frame（状态 + 存证标记 + 哈希）与插入 chain_tx_record
 *       必须原子完成，否则会出现「车架显示已上链但没有交易记录」的不一致。</li>
 *   <li><b>远程调用不放事务内</b>：区块链写入是秒级网络 IO，放在事务里会让数据库连接被长时间占用。
 *       所以本类的 recordToChain 只负责本地事务，链上调用由调用方在事务提交后触发。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttestService {

    /** 质检项：外观 / 尺寸 / 力学，三项全部 PASS 才允许上链 */
    public static final List<String> QC_ITEMS = List.of("APPEARANCE", "DIMENSION", "MECHANIC");
    public static final String STATUS_INSPECTED = "INSPECTED";

    private final InspectionRecordMapper inspectionMapper;
    private final FrameMapper frameMapper;
    private final ChainTxRecordMapper chainTxMapper;
    private final TraceService traceService;
    private final ChainService chainService;

    /**
     * 自身代理引用：用于在类内部触发 recordToChain 时仍能走 Spring 事务代理。
     * 直接写 this.recordToChain(...) 不会经过代理，@Transactional 会静默失效。
     */
    private final ObjectProvider<AttestService> self;

    /**
     * 三项质检全部 PASS 且无 FAIL 时自动上链；已上链过的车架跳过。
     *
     * <p>本方法只做判断与编排，不加事务：真正的原子写入由 recordToChain 提供。
     * 这样上链失败时只有上链那次事务回滚，不会牵连调用方的质检记录。</p>
     *
     * @return true 表示本次完成了上链
     */
    public boolean autoRecordToChain(Frame frame) {
        List<InspectionRecord> records = inspectionMapper.selectList(
                new LambdaQueryWrapper<InspectionRecord>().eq(InspectionRecord::getFrameId, frame.getId()));
        Set<String> passedItems = records.stream()
                .filter(r -> "PASS".equals(r.getResult()))
                .map(InspectionRecord::getItem)
                .collect(Collectors.toSet());
        boolean allPassed = passedItems.containsAll(QC_ITEMS);
        boolean hasFail = records.stream().anyMatch(r -> "FAIL".equals(r.getResult()));
        if (!allPassed || hasFail || Integer.valueOf(1).equals(frame.getHashRecorded())) {
            return false;
        }
        try {
            self.getObject().recordToChain(frame);
            return true;
        } catch (Exception e) {
            // 自动上链失败不影响质检记录本身：本轮不阻断，留待管理员用「手动上链」兜底
            log.error("自动上链失败 serial={}", frame.getSerialNo(), e);
            return false;
        }
    }

    /**
     * 计算当前链下数据哈希并写入链上，成功后回写车架存证状态与交易记录。
     *
     * <p>注意：本方法内部会调用区块链，事务会覆盖这段远程调用。当前用于毕设演示场景（数据量小、
     * 单机部署）可以接受；生产环境应改为本地写库提交后再异步上链 + 状态回填。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> recordToChain(Frame frame) throws Exception {
        if (Integer.valueOf(1).equals(frame.getHashRecorded())) {
            throw new BizException("该车架已完成上链存证，不可重复上链");
        }
        // 按当前口径（v2，含碳布批次）计算并记录版本，供日后校验时选用同一公式
        frame.setHashVersion(TraceService.HASH_VERSION_CURRENT);
        String hash = traceService.computeCurrentHash(frame);
        String version = "v" + TraceService.HASH_VERSION_CURRENT;

        // 1. 先上链：链上交易不可回滚，必须最先确认成功
        ChainService.TxResult tx = chainService.recordHash(frame.getSerialNo(), hash, version);

        // 2. 再写本地：以下两步在同一事务内，要么都成功要么都回滚
        frame.setCurrentHash(hash);
        frame.setHashRecorded(1);
        frame.setStatus(STATUS_INSPECTED);
        frameMapper.updateById(frame);

        ChainTxRecord txRecord = new ChainTxRecord();
        txRecord.setFrameId(frame.getId());
        txRecord.setSerialNo(frame.getSerialNo());
        txRecord.setTxHash(tx.txHash);
        txRecord.setBlockNumber(tx.blockNumber);
        txRecord.setOnchainHash("0x" + hash);
        txRecord.setDataVersion(version);
        txRecord.setStatus("SUCCESS");
        txRecord.setTxTime(LocalDateTime.now());
        chainTxMapper.insert(txRecord);

        // 3. 清缓存放到最后：即使前面回滚，清一次缓存也无副作用；提交后再补清一次防并发写回
        traceService.evictCacheSafely(frame.getSerialNo());

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("serialNo", frame.getSerialNo());
        resp.put("sha256", hash);
        resp.put("hashVersion", version);
        resp.put("txHash", tx.txHash);
        resp.put("blockNumber", tx.blockNumber);
        resp.put("message", "关键质量数据哈希已上链存证");
        return resp;
    }
}
