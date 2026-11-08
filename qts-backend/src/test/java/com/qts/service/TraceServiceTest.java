package com.qts.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qts.entity.Frame;
import com.qts.entity.InspectionRecord;
import com.qts.entity.MaterialBatch;
import com.qts.entity.ProcessRecord;
import com.qts.mapper.FrameMapper;
import com.qts.mapper.InspectionRecordMapper;
import com.qts.mapper.LogisticsRecordMapper;
import com.qts.mapper.MaterialBatchMapper;
import com.qts.mapper.ProcessRecordMapper;
import com.qts.mapper.UserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 溯源核心逻辑单元测试：覆盖「哈希计算的确定性与敏感性」+「验真的三种结论」。
 *
 * <p>这两块是整个系统的立身之本 —— 如果哈希会因为查询顺序不同而变化，
 * 那么正常的车架也会被判成「已篡改」，溯源就失去意义。</p>
 */
@ExtendWith(MockitoExtension.class)
class TraceServiceTest {

    @Mock private FrameMapper frameMapper;
    @Mock private MaterialBatchMapper batchMapper;
    @Mock private ProcessRecordMapper processMapper;
    @Mock private InspectionRecordMapper inspectionMapper;
    @Mock private LogisticsRecordMapper logisticsMapper;
    @Mock private UserMapper userMapper;
    @Mock private ChainService chainService;
    @Mock private StringRedisTemplate redis;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks private TraceService traceService;

    // ---------- 构造测试数据 ----------

    private Frame sampleFrame() {
        Frame f = new Frame();
        f.setId(1L);
        f.setSerialNo("FR2026001");
        f.setModel("S-WORKS TARMAC");
        f.setSize("54");
        f.setBatchId(9L);
        f.setWorkshop("一号车间");
        f.setProductionDate(LocalDate.of(2026, 3, 15));
        f.setStatus("INSPECTED");
        f.setHashRecorded(1);
        f.setHashVersion(TraceService.HASH_VERSION_CURRENT);
        return f;
    }

    private MaterialBatch sampleBatch() {
        MaterialBatch b = new MaterialBatch();
        b.setId(9L);
        b.setBatchNo("CF-2026-03");
        b.setMaterialName("T800 碳纤维布");
        b.setSpec("300g/m2");
        b.setSupplier("东丽");
        b.setIncomingDate(LocalDate.of(2026, 2, 1));
        b.setQcStatus("PASS");
        return b;
    }

    private List<ProcessRecord> processes(String... order) {
        List<ProcessRecord> list = new ArrayList<>();
        long id = 1;
        for (String type : order) {
            ProcessRecord p = new ProcessRecord();
            p.setId(id++);
            p.setFrameId(1L);
            p.setProcessType(type);
            p.setRecordData(type + "-data");
            p.setRecordTime(LocalDateTime.of(2026, 3, 1, 10, 0));
            list.add(p);
        }
        return list;
    }

    private List<InspectionRecord> inspections() {
        List<InspectionRecord> list = new ArrayList<>();
        String[] items = {"APPEARANCE", "DIMENSION", "MECHANIC"};
        for (int i = 0; i < items.length; i++) {
            InspectionRecord r = new InspectionRecord();
            r.setId((long) (i + 1));
            r.setFrameId(1L);
            r.setItem(items[i]);
            r.setResult("PASS");
            r.setReportNo("QC" + i);
            r.setInspectTime(LocalDateTime.of(2026, 3, 10, 9, 0));
            list.add(r);
        }
        return list;
    }

    private void stubData(List<ProcessRecord> procs, List<InspectionRecord> inspects) {
        when(batchMapper.selectById(9L)).thenReturn(sampleBatch());
        when(processMapper.selectList(any())).thenReturn(procs);
        when(inspectionMapper.selectList(any())).thenReturn(inspects);
    }

    /** v1 口径不查批次，所以不能打桩批次查询，否则 Mockito 会判定为多余打桩 */
    private void stubDataWithoutBatch(List<ProcessRecord> procs, List<InspectionRecord> inspects) {
        when(processMapper.selectList(any())).thenReturn(procs);
        when(inspectionMapper.selectList(any())).thenReturn(inspects);
    }

    // ---------- 哈希计算 ----------

    @Test
    @DisplayName("同一份数据多次计算结果一致")
    void computeCurrentHash_shouldBeStable() {
        stubData(processes("LAYUP", "CURING", "NDT"), inspections());
        Frame f = sampleFrame();
        assertEquals(traceService.computeCurrentHash(f), traceService.computeCurrentHash(f));
    }

    @Test
    @DisplayName("工序记录查询顺序不同，哈希结果必须相同")
    void computeCurrentHash_shouldIgnoreQueryOrder() {
        Frame f = sampleFrame();

        stubData(processes("LAYUP", "CURING", "NDT"), inspections());
        String h1 = traceService.computeCurrentHash(f);

        stubData(processes("NDT", "LAYUP", "CURING"), inspections());
        String h2 = traceService.computeCurrentHash(f);

        assertEquals(h1, h2, "数据库返回顺序不一样就变哈希，会导致正常数据被误判为篡改");
    }

    @Test
    @DisplayName("质检结果被改动，哈希必须变化")
    void computeCurrentHash_shouldDetectInspectionTamper() {
        Frame f = sampleFrame();

        stubData(processes("LAYUP", "CURING", "NDT"), inspections());
        String before = traceService.computeCurrentHash(f);

        List<InspectionRecord> tampered = inspections();
        tampered.get(0).setResult("FAIL");   // 模拟链下数据被改
        stubData(processes("LAYUP", "CURING", "NDT"), tampered);
        String after = traceService.computeCurrentHash(f);

        assertNotEquals(before, after);
    }

    @Test
    @DisplayName("原料批次被替换，哈希必须变化（碳布来源是质量源头）")
    void computeCurrentHash_shouldCoverMaterialBatch() {
        Frame f = sampleFrame();

        stubData(processes("LAYUP", "CURING", "NDT"), inspections());
        String before = traceService.computeCurrentHash(f);

        MaterialBatch changed = sampleBatch();
        changed.setSupplier("某非授权供应商");
        when(batchMapper.selectById(9L)).thenReturn(changed);
        String after = traceService.computeCurrentHash(f);

        assertNotEquals(before, after, "换了碳布供应商但哈希不变，等于这条溯源链是断的");
    }

    @Test
    @DisplayName("历史数据按 v1 口径校验：不含批次，公式升级不能把它判成篡改")
    void verify_shouldUseLegacyFormulaForV1Frame() throws Exception {
        Frame f = sampleFrame();
        f.setHashVersion(1);                 // 老车架：存证时用的 v1 公式
        stubDataWithoutBatch(processes("LAYUP", "CURING", "NDT"), inspections());

        // 链上存的是当年用 v1 算出来的值
        String onChain = traceService.computeHash(f, 1);
        when(chainService.getHash("FR2026001")).thenReturn(onChain);

        Map<String, Object> v = traceService.verify(f);

        assertEquals("VALID", v.get("result"), "公式升级后拿 v2 去校验 v1 老数据，会全员误报已篡改");
        assertEquals(1, v.get("hashVersion"));
    }

    @Test
    @DisplayName("v1 与 v2 口径算出的哈希必须不同（批次确实进了 v2）")
    void computeHash_shouldDifferBetweenVersions() {
        Frame f = sampleFrame();
        stubData(processes("LAYUP", "CURING", "NDT"), inspections());

        String v1 = traceService.computeHash(f, 1);
        String v2 = traceService.computeHash(f, 2);

        assertNotEquals(v1, v2);
    }

    @Test
    @DisplayName("v1 口径忽略批次：换供应商不影响老车架的校验结果")
    void computeHash_v1ShouldIgnoreBatch() {
        Frame f = sampleFrame();
        stubDataWithoutBatch(processes("LAYUP"), inspections());
        String before = traceService.computeHash(f, 1);

        f.setBatchId(999L);                  // v1 公式完全不读批次，换批次不应改变结果
        String after = traceService.computeHash(f, 1);

        assertEquals(before, after);
    }

    // ---------- 验真三态 ----------
    @Test
    @DisplayName("未上链的车架 → NOT_RECORDED")
    void verify_shouldReportNotRecorded() throws Exception {
        Frame f = sampleFrame();
        f.setHashRecorded(0);
        stubData(processes("LAYUP"), inspections());

        Map<String, Object> v = traceService.verify(f);

        assertEquals("NOT_RECORDED", v.get("result"));
        assertEquals(false, v.get("recorded"));
    }

    @Test
    @DisplayName("链上链下哈希一致 → VALID")
    void verify_shouldReportValid() throws Exception {
        Frame f = sampleFrame();
        stubData(processes("LAYUP", "CURING", "NDT"), inspections());
        String current = traceService.computeCurrentHash(f);
        when(chainService.getHash("FR2026001")).thenReturn(current);

        Map<String, Object> v = traceService.verify(f);

        assertEquals("VALID", v.get("result"));
        assertEquals(current, v.get("onChainHash"));
    }

    @Test
    @DisplayName("链上链下哈希不一致 → TAMPERED")
    void verify_shouldReportTampered() throws Exception {
        Frame f = sampleFrame();
        stubData(processes("LAYUP", "CURING", "NDT"), inspections());
        when(chainService.getHash("FR2026001"))
                .thenReturn("0000000000000000000000000000000000000000000000000000000000000000");

        Map<String, Object> v = traceService.verify(f);

        assertEquals("TAMPERED", v.get("result"));
    }

    @Test
    @DisplayName("标记已上链但链上查不到 → 按未存证处理，不误报篡改")
    void verify_shouldReportNotRecordedWhenChainReturnNull() throws Exception {
        Frame f = sampleFrame();
        stubData(processes("LAYUP"), inspections());
        when(chainService.getHash("FR2026001")).thenReturn(null);

        Map<String, Object> v = traceService.verify(f);

        assertEquals("NOT_RECORDED", v.get("result"));
    }
}
