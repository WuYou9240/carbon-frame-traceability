package com.qts.service;

import com.qts.common.BizException;
import com.qts.entity.Frame;
import com.qts.entity.InspectionRecord;
import com.qts.mapper.ChainTxRecordMapper;
import com.qts.mapper.FrameMapper;
import com.qts.mapper.InspectionRecordMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 上链存证规则单元测试：什么时候该自动上链、什么时候必须拒绝重复上链。
 * 这些是业务规则，不该靠人工点页面去试。
 */
@ExtendWith(MockitoExtension.class)
class AttestServiceTest {

    @Mock private InspectionRecordMapper inspectionMapper;
    @Mock private FrameMapper frameMapper;
    @Mock private ChainTxRecordMapper chainTxMapper;
    @Mock private TraceService traceService;
    @Mock private ChainService chainService;
    @Mock private ObjectProvider<AttestService> self;
    @Mock private AttestService selfProxy;

    @InjectMocks private AttestService attestService;

    private Frame frame(boolean recorded) {
        Frame f = new Frame();
        f.setId(1L);
        f.setSerialNo("FR2026001");
        f.setStatus("CURED");
        f.setHashRecorded(recorded ? 1 : 0);
        return f;
    }

    private List<InspectionRecord> inspections(String... results) {
        String[] items = {"APPEARANCE", "DIMENSION", "MECHANIC"};
        List<InspectionRecord> list = new ArrayList<>();
        for (int i = 0; i < results.length; i++) {
            InspectionRecord r = new InspectionRecord();
            r.setId((long) (i + 1));
            r.setFrameId(1L);
            r.setItem(items[i]);
            r.setResult(results[i]);
            r.setInspectTime(LocalDateTime.now());
            list.add(r);
        }
        return list;
    }

    @Test
    @DisplayName("三项质检全部 PASS → 触发上链")
    void autoRecordToChain_shouldFireWhenAllPassed() throws Exception {
        when(inspectionMapper.selectList(any())).thenReturn(inspections("PASS", "PASS", "PASS"));
        when(self.getObject()).thenReturn(selfProxy);

        boolean fired = attestService.autoRecordToChain(frame(false));

        assertTrue(fired);
        verify(selfProxy, times(1)).recordToChain(any(Frame.class));
    }

    @Test
    @DisplayName("只有两项通过 → 不上链")
    void autoRecordToChain_shouldNotFireWhenIncomplete() throws Exception {
        when(inspectionMapper.selectList(any())).thenReturn(inspections("PASS", "PASS"));

        assertFalse(attestService.autoRecordToChain(frame(false)));
        verify(selfProxy, never()).recordToChain(any(Frame.class));
    }

    @Test
    @DisplayName("存在一项 FAIL → 不上链（即使三项都有记录）")
    void autoRecordToChain_shouldNotFireWhenAnyFailed() throws Exception {
        when(inspectionMapper.selectList(any())).thenReturn(inspections("PASS", "FAIL", "PASS"));

        assertFalse(attestService.autoRecordToChain(frame(false)));
        verify(selfProxy, never()).recordToChain(any(Frame.class));
    }

    @Test
    @DisplayName("已上链的车架 → 跳过，不重复上链")
    void autoRecordToChain_shouldSkipWhenAlreadyRecorded() throws Exception {
        when(inspectionMapper.selectList(any())).thenReturn(inspections("PASS", "PASS", "PASS"));

        assertFalse(attestService.autoRecordToChain(frame(true)));
        verify(selfProxy, never()).recordToChain(any(Frame.class));
    }

    @Test
    @DisplayName("上链失败只记日志，不抛出异常、不阻断质检流程")
    void autoRecordToChain_shouldSwallowChainError() throws Exception {
        when(inspectionMapper.selectList(any())).thenReturn(inspections("PASS", "PASS", "PASS"));
        when(self.getObject()).thenReturn(selfProxy);
        when(selfProxy.recordToChain(any(Frame.class))).thenThrow(new RuntimeException("节点连接超时"));

        assertDoesNotThrow(() -> attestService.autoRecordToChain(frame(false)));
        assertFalse(attestService.autoRecordToChain(frame(false)));
    }

    @Test
    @DisplayName("重复上链必须被拒绝（幂等保护）")
    void recordToChain_shouldRejectDuplicate() {
        BizException ex = assertThrows(BizException.class,
                () -> attestService.recordToChain(frame(true)));
        assertTrue(ex.getMessage().contains("不可重复上链"));
    }
}
