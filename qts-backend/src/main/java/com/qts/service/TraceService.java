package com.qts.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qts.common.BizException;
import com.qts.common.HashUtil;
import com.qts.entity.*;
import com.qts.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 溯源服务：链下关键数据哈希计算 + 链上链下哈希比对验真 + 全链路质量档案拼装
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TraceService {

    private final FrameMapper frameMapper;
    private final MaterialBatchMapper batchMapper;
    private final ProcessRecordMapper processMapper;
    private final InspectionRecordMapper inspectionMapper;
    private final LogisticsRecordMapper logisticsMapper;
    private final UserMapper userMapper;
    private final ChainService chainService;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    @Value("${app.trace-cache-ttl-seconds}")
    private long cacheTtl;

    public static final String CACHE_KEY_PREFIX = "trace:";
    public static final String HOT_KEY = "trace:hot";

    /** 当前哈希口径版本：2 = 含碳布批次（v1 为早期不含批次的公式） */
    public static final int HASH_VERSION_CURRENT = 2;

    /**
     * 计算车架当前链下关键数据的 SHA-256（规范化拼接后哈希）。
     *
     * <p><b>口径版本很重要</b>：本方法按车架存证时的 hashVersion 选择公式。哈希公式一旦升级，
     * 若仍用新公式重算老数据，会把全部历史存证误判为「已篡改」——那是公式变了，不是数据被改。
     * 所以老车架继续按 v1 校验，新车架按 v2 存证。</p>
     */
    public String computeCurrentHash(Frame frame) {
        int version = frame.getHashVersion() == null ? 1 : frame.getHashVersion();
        return computeHash(frame, version);
    }

    /**
     * 按指定版本计算哈希。
     *
     * <p>哈希覆盖范围：v1 = 车架档案 + 全部工序 + 全部质检；v2 = 在 v1 基础上追加原料批次。
     * 拼接顺序按业务键固定排序，保证同一份数据在任何机器上算出的哈希一致。</p>
     *
     * <p>物流记录<b>不纳入</b>本哈希：物流是车架售出后的追加事件（insert-only，无修改接口），
     * 属于流通环节而非生产质量数据。若纳入主哈希，正常售出也会让已上链的车架被判为「已篡改」。
     * 如需校验流通环节，应单独做流通存证，而不是混入质量哈希。</p>
     */
    public String computeHash(Frame frame, int version) {
        StringBuilder sb = new StringBuilder();
        sb.append(HashUtil.canonical("FRAME", frame.getSerialNo(), nvl(frame.getModel()), nvl(frame.getSize()),
                nvl(frame.getWorkshop()), frame.getProductionDate() == null ? "" : frame.getProductionDate().toString()));

        // 原料批次：碳纤维布批次直接决定车架力学性能，是质量溯源链的源头，必须纳入哈希（v2 起）
        if (version >= 2 && frame.getBatchId() != null) {
            MaterialBatch batch = batchMapper.selectById(frame.getBatchId());
            if (batch != null) {
                sb.append(HashUtil.canonical("BATCH", nvl(batch.getBatchNo()), nvl(batch.getMaterialName()),
                        nvl(batch.getSpec()), nvl(batch.getSupplier()),
                        batch.getIncomingDate() == null ? "" : batch.getIncomingDate().toString(),
                        nvl(batch.getQcStatus())));
            }
        }

        List<ProcessRecord> processes = processMapper.selectList(
                new LambdaQueryWrapper<ProcessRecord>().eq(ProcessRecord::getFrameId, frame.getId()));
        processes.stream()
                .sorted(Comparator.comparing(ProcessRecord::getProcessType, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(ProcessRecord::getId))
                .forEach(p -> sb.append(HashUtil.canonical("PROC", p.getProcessType(), nvl(p.getRecordData()),
                        p.getRecordTime() == null ? "" : p.getRecordTime().toString())));

        List<InspectionRecord> inspections = inspectionMapper.selectList(
                new LambdaQueryWrapper<InspectionRecord>().eq(InspectionRecord::getFrameId, frame.getId()));
        inspections.stream()
                .sorted(Comparator.comparing(InspectionRecord::getItem, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(InspectionRecord::getId))
                .forEach(i -> sb.append(HashUtil.canonical("QC", i.getItem(), i.getResult(),
                        nvl(i.getDefectDesc()), i.getInspectTime() == null ? "" : i.getInspectTime().toString())));

        return HashUtil.sha256Hex(sb.toString());
    }

    /**
     * 验真：链下实时哈希 与 链上存证哈希 比对
     */
    public Map<String, Object> verify(Frame frame) throws Exception {
        String offChainHash = computeCurrentHash(frame);
        boolean recorded = frame.getHashRecorded() != null && frame.getHashRecorded() == 1;
        String onChainHash = recorded ? chainService.getHash(frame.getSerialNo()) : null;

        Map<String, Object> verify = new LinkedHashMap<>();
        verify.put("recorded", recorded);
        verify.put("hashVersion", frame.getHashVersion() == null ? 1 : frame.getHashVersion());
        verify.put("offChainHash", offChainHash);
        verify.put("onChainHash", onChainHash);
        if (!recorded) {
            verify.put("result", "NOT_RECORDED");
            verify.put("message", "该车架关键质量数据尚未上链存证");
        } else if (onChainHash == null) {
            verify.put("result", "NOT_RECORDED");
            verify.put("message", "链上未查询到该车架存证记录");
        } else if (onChainHash.equalsIgnoreCase(offChainHash)) {
            verify.put("result", "VALID");
            verify.put("message", "数据已上链存证，链上链下哈希一致，未被篡改");
        } else {
            verify.put("result", "TAMPERED");
            verify.put("message", "警告：链上链下哈希不一致，链下数据可能被篡改！");
        }
        return verify;
    }

    /**
     * 消费者扫码查询：全链路质量档案 + 验真结果
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> traceBySerial(String serialNo, boolean countScan) throws Exception {
        String cacheKey = CACHE_KEY_PREFIX + serialNo;
        if (countScan) {
            try {
                redis.opsForZSet().incrementScore(HOT_KEY, serialNo, 1);
            } catch (Exception e) {
                log.warn("扫码热度计数失败 serialNo={}: {}", serialNo, e.getMessage());
            }
        }
        // 缓存只是加速手段，不应成为单点故障：Redis 异常时降级为直接查库
        String cached = null;
        try {
            cached = redis.opsForValue().get(cacheKey);
        } catch (Exception e) {
            log.warn("读取溯源缓存失败，降级为直查数据库 serialNo={}: {}", serialNo, e.getMessage());
        }
        if (cached != null) {
            return objectMapper.readValue(cached, Map.class);
        }

        Frame frame = frameMapper.selectOne(new LambdaQueryWrapper<Frame>().eq(Frame::getSerialNo, serialNo));
        if (frame == null) {
            throw new BizException("未找到序列号为 " + serialNo + " 的车架");
        }

        Map<String, Object> vo = new LinkedHashMap<>();
        vo.put("frame", frame);
        if (frame.getBatchId() != null) {
            vo.put("batch", batchMapper.selectById(frame.getBatchId()));
        }
        vo.put("processes", processMapper.selectList(
                new LambdaQueryWrapper<ProcessRecord>().eq(ProcessRecord::getFrameId, frame.getId()).orderByAsc(ProcessRecord::getId)));
        vo.put("inspections", inspectionMapper.selectList(
                new LambdaQueryWrapper<InspectionRecord>().eq(InspectionRecord::getFrameId, frame.getId()).orderByAsc(InspectionRecord::getId)));
        vo.put("logistics", logisticsMapper.selectList(
                new LambdaQueryWrapper<LogisticsRecord>().eq(LogisticsRecord::getFrameId, frame.getId()).orderByAsc(LogisticsRecord::getId)));

        // 相关人员姓名
        for (ProcessRecord p : (List<ProcessRecord>) vo.get("processes")) {
            p.setRemark(null); // 减少冗余
        }
        vo.put("verify", verify(frame));

        String json = objectMapper.writeValueAsString(vo);
        try {
            redis.opsForValue().set(cacheKey, json, Duration.ofSeconds(cacheTtl));
        } catch (Exception e) {
            // 写缓存失败不影响本次查询结果，下次请求自然回到冷路径
            log.warn("写入溯源缓存失败 serialNo={}: {}", serialNo, e.getMessage());
        }
        return objectMapper.readValue(json, Map.class);
    }

    /** 数据变更后清除溯源缓存 */
    public void evictCache(String serialNo) {
        try {
            redis.delete(CACHE_KEY_PREFIX + serialNo);
        } catch (Exception e) {
            // 清缓存失败不能阻断写操作：缓存有 TTL 兜底，最多短暂读到旧结论
            log.warn("清除溯源缓存失败 serialNo={}: {}", serialNo, e.getMessage());
        }
    }

    /**
     * 写操作专用的缓存清除：立刻清一次，并在事务提交后再清一次。
     *
     * 为什么需要两次——写接口的事务尚未提交时就清了缓存，此时若有并发查询进来，
     * 它读到的还是未提交前的旧数据，算完哈希又把旧结果写回 Redis；等事务提交，
     * 缓存里留的就是脏数据，TTL 内一直显示错误结论（典型现象：点了「恢复数据」，
     * 验真仍报 TAMPERED，要等 5 分钟缓存过期才转绿）。
     * 提交后再清一次，可以把这类并发写回的脏缓存抹掉。
     */
    public void evictCacheSafely(String serialNo) {
        evictCache(serialNo);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evictCache(serialNo);
                }
            });
        }
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }
}
