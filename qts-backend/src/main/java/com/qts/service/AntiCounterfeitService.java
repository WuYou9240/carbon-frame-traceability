package com.qts.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qts.config.QrBaseUrlResolver;
import com.qts.entity.Frame;
import com.qts.entity.QrCodeRecord;
import com.qts.mapper.FrameMapper;
import com.qts.mapper.QrCodeRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 二维码防伪校验服务。
 *
 * <p>防伪不能只看「能不能扫出一张页面」——仿造者可以自己印一张指向任意地址的二维码。
 * 所以这里做四层校验，任何一层不通过都给出明确结论：</p>
 * <ol>
 *   <li><b>序列号有效性</b>：该序列号在系统中是否存在车架档案（挡住凭空编造的编号）</li>
 *   <li><b>二维码内容一致性</b>：库中二维码内容是否等于系统按当前基地址应生成的内容（挡住伪造链接）</li>
 *   <li><b>链上存证状态</b>：该车架是否已完成质量数据上链（未上链则无法验真，不能判定为真品）</li>
 *   <li><b>数据完整性</b>：链下实时哈希与链上存证哈希是否一致（挡住入库后改数据）</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AntiCounterfeitService {

    private final FrameMapper frameMapper;
    private final QrCodeRecordMapper qrMapper;
    private final QrBaseUrlResolver qrBaseUrlResolver;
    private final TraceService traceService;

    public Map<String, Object> verifyBySerial(String serialNo) throws Exception {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("serialNo", serialNo);

        Frame frame = frameMapper.selectOne(
                new LambdaQueryWrapper<Frame>().eq(Frame::getSerialNo, serialNo));

        // 第 1 层：序列号是否存在
        if (frame == null) {
            resp.put("exists", false);
            resp.put("level", "NOT_FOUND");
            resp.put("levelText", "查无此码");
            resp.put("message", "系统中不存在该序列号，该二维码可能是伪造的");
            resp.put("checks", List.of(check("序列号有效性", false, "系统中不存在该车架档案")));
            return resp;
        }
        resp.put("exists", true);
        resp.put("frameId", frame.getId());
        resp.put("model", frame.getModel());
        resp.put("size", frame.getSize());

        // 第 2 层：二维码内容一致性
        String expected = qrBaseUrlResolver.base() + serialNo;
        QrCodeRecord qr = qrMapper.selectOne(
                new LambdaQueryWrapper<QrCodeRecord>().eq(QrCodeRecord::getFrameId, frame.getId()));
        boolean qrExists = qr != null && qr.getQrContent() != null;
        boolean qrValid = qrExists && expected.equals(qr.getQrContent());
        resp.put("qrExists", qrExists);
        resp.put("qrContent", qrExists ? qr.getQrContent() : null);
        resp.put("expectedContent", expected);
        resp.put("qrContentValid", qrValid);

        // 第 3、4 层：链上存证与数据完整性，交给溯源服务做哈希比对
        Map<String, Object> verify = traceService.verify(frame);
        boolean recorded = Boolean.TRUE.equals(verify.get("recorded"));
        String result = String.valueOf(verify.get("result"));
        boolean hashMatched = "VALID".equals(result);

        resp.put("recorded", recorded);
        resp.put("hashVersion", verify.get("hashVersion"));
        resp.put("offChainHash", verify.get("offChainHash"));
        resp.put("onChainHash", verify.get("onChainHash"));
        resp.put("hashMatched", hashMatched);

        List<Map<String, Object>> checks = new ArrayList<>();
        checks.add(check("序列号有效性", true, "系统中存在该车架档案 " + serialNo));
        checks.add(check("二维码内容一致性", qrValid,
                qrValid ? "二维码内容与系统记录一致" : "二维码内容与系统记录不一致，可能是伪造或旧码"));
        checks.add(check("链上存证状态", recorded,
                recorded ? "已完成质量数据上链存证" : "尚未上链存证，无法验真"));
        checks.add(check("数据完整性", hashMatched,
                hashMatched ? "链上链下哈希一致，质量数据未被篡改"
                        : "链上链下哈希不一致，链下数据可能被篡改"));
        resp.put("checks", checks);

        // 综合结论：按严重程度从高到低判定
        if (!qrValid) {
            resp.put("level", "SUSPECT");
            resp.put("levelText", "存疑");
            resp.put("message", "该二维码内容与系统记录不符，请核对标签来源");
        } else if (!recorded) {
            resp.put("level", "NOT_RECORDED");
            resp.put("levelText", "未存证");
            resp.put("message", "该车架质量数据尚未上链，无法进行防伪验证");
        } else if (!hashMatched) {
            resp.put("level", "TAMPERED");
            resp.put("levelText", "数据异常");
            resp.put("message", "链上链下哈希不一致，该车架质量数据存在被篡改风险");
        } else {
            resp.put("level", "AUTHENTIC");
            resp.put("levelText", "真品");
            resp.put("message", "四层校验全部通过，该车架为正品且质量数据完整可信");
        }
        return resp;
    }

    private Map<String, Object> check(String name, boolean pass, String detail) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("pass", pass);
        m.put("detail", detail);
        return m;
    }
}
