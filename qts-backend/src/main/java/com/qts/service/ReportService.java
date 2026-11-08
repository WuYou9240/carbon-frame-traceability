package com.qts.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qts.entity.Frame;
import com.qts.entity.InspectionRecord;
import com.qts.entity.MaterialBatch;
import com.qts.entity.ProcessRecord;
import com.qts.entity.User;
import com.qts.mapper.FrameMapper;
import com.qts.mapper.InspectionRecordMapper;
import com.qts.mapper.MaterialBatchMapper;
import com.qts.mapper.ProcessRecordMapper;
import com.qts.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 车架质检报告生成服务。
 *
 * <p>输出一份 A4 版式、可直接打印（Ctrl+P → 另存为 PDF）的 HTML 报告。
 * 之所以生成 HTML 而不是直接生成 PDF：浏览器自带打印排版最稳，
 * 中文字体、分页、表格跨页都由浏览器处理，不额外引入 PDF 库依赖。</p>
 *
 * <p>报告由六部分构成：报告抬头、车架档案、原料批次、生产工序、质量检验、区块链存证摘要。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final FrameMapper frameMapper;
    private final ProcessRecordMapper processMapper;
    private final InspectionRecordMapper inspectionMapper;
    private final MaterialBatchMapper batchMapper;
    private final UserMapper userMapper;
    private final TraceService traceService;

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final Map<String, String> PROCESS_TEXT = Map.of(
            "CLOTH_SELECTION", "碳布裁剪",
            "AUTO_CUTTING", "自动裁切",
            "LAYUP", "铺层",
            "CURING", "固化成型",
            "CNC", "CNC 精加工",
            "NDT", "无损检测");

    private static final Map<String, String> ITEM_TEXT = Map.of(
            "APPEARANCE", "外观检查",
            "DIMENSION", "尺寸检查",
            "MECHANIC", "力学性能");

    private static final Map<String, String> STATUS_TEXT = Map.of(
            "IN_PROD", "在产",
            "CURED", "已固化",
            "INSPECTED", "已质检",
            "SHIPPED", "已发货");

    public String buildHtml(Long frameId) throws Exception {
        Frame frame = frameMapper.selectById(frameId);
        if (frame == null) {
            return errorPage("车架不存在");
        }
        MaterialBatch batch = frame.getBatchId() == null ? null : batchMapper.selectById(frame.getBatchId());
        List<ProcessRecord> processes = processMapper.selectList(
                new LambdaQueryWrapper<ProcessRecord>()
                        .eq(ProcessRecord::getFrameId, frameId).orderByAsc(ProcessRecord::getId));
        List<InspectionRecord> inspections = inspectionMapper.selectList(
                new LambdaQueryWrapper<InspectionRecord>()
                        .eq(InspectionRecord::getFrameId, frameId).orderByAsc(InspectionRecord::getId));

        long failCount = inspections.stream().filter(i -> !"PASS".equals(i.getResult())).count();
        boolean concluded = !inspections.isEmpty();
        String conclusion = !concluded ? "待检验"
                : (failCount == 0 ? "合格" : "不合格（存在 " + failCount + " 项不合格）");

        Map<String, Object> verify;
        try {
            verify = traceService.verify(frame);
        } catch (Exception e) {
            log.warn("报告生成时验真失败 frameId={}: {}", frameId, e.getMessage());
            verify = Map.of("result", "CHAIN_UNREACHABLE", "message", "区块链节点不可达，未能完成验真");
        }

        StringBuilder h = new StringBuilder();
        h.append("""
                <!DOCTYPE html>
                <html lang="zh-CN"><head><meta charset="UTF-8">
                <title>车架质检报告</title>
                <style>
                  @page { size: A4; margin: 18mm 16mm 16mm 16mm; }
                  * { box-sizing: border-box; }
                  body { font-family: "宋体", SimSun, serif; font-size: 10.5pt; color: #111; margin: 0; }
                  .sheet { width: 178mm; margin: 0 auto; }
                  h1 { font-family: "黑体", SimHei, sans-serif; font-size: 18pt; text-align: center;
                       letter-spacing: 4px; margin: 0 0 4mm; }
                  .sub { text-align: center; font-size: 9pt; color: #444; margin-bottom: 6mm; }
                  .meta { width: 100%; border-collapse: collapse; margin-bottom: 4mm; font-size: 9.5pt; }
                  .meta td { padding: 1.5mm 2mm; }
                  h2 { font-family: "黑体", SimHei, sans-serif; font-size: 11pt; margin: 5mm 0 2mm;
                       border-left: 3px solid #1a3d6d; padding-left: 2.5mm; }
                  table.grid { width: 100%; border-collapse: collapse; font-size: 9.5pt; }
                  table.grid th, table.grid td { border: 0.6pt solid #666; padding: 1.6mm 2mm; text-align: left; }
                  table.grid th { background: #eef2f7; font-weight: normal; width: 28%; }
                  table.list { width: 100%; border-collapse: collapse; font-size: 9pt; }
                  table.list th, table.list td { border: 0.6pt solid #666; padding: 1.4mm 2mm; }
                  table.list th { background: #eef2f7; font-weight: normal; text-align: center; }
                  .pass { color: #0a6b2e; }
                  .fail { color: #b3261e; font-weight: bold; }
                  .mono { font-family: Consolas, "Courier New", monospace; font-size: 8.5pt; word-break: break-all; }
                  .verdict { margin-top: 3mm; padding: 3mm; border: 0.8pt solid #1a3d6d; text-align: center;
                             font-family: "黑体", SimHei, sans-serif; font-size: 12pt; letter-spacing: 2px; }
                  .sign { margin-top: 10mm; width: 100%; font-size: 9.5pt; }
                  .sign td { padding-top: 8mm; }
                  .foot { margin-top: 6mm; font-size: 8pt; color: #777; text-align: center; }
                  .toolbar { text-align: right; margin-bottom: 4mm; }
                  .toolbar button { font-size: 10pt; padding: 2mm 6mm; cursor: pointer; }
                  @media print { .toolbar { display: none; } }
                </style></head><body><div class="sheet">
                <div class="toolbar"><button onclick="window.print()">打印 / 另存为 PDF</button></div>
                <h1>碳纤维车架质量检验报告</h1>
                <div class="sub">公路自行车碳纤维车架质量溯源系统</div>
                """);

        // 抬头信息
        // 报告编号按「车架序列号 + 生成日期」生成：inspection.report_no 是单条检验记录的编号，
        // 拿它当整份报告的编号语义不对（同一车架三项检验会有三个编号）
        String reportNo = "QC-" + frame.getSerialNo() + "-"
                + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        h.append("<table class=\"meta\"><tr>")
                .append("<td style=\"width:34%\">报告编号：" + esc(reportNo) + "</td>")
                .append("<td style=\"width:33%\">生成时间：" + LocalDateTime.now().format(DATETIME) + "</td>")
                .append("<td>报告状态：<span class=\"").append(concluded && failCount == 0 ? "pass" : "fail")
                .append("\">").append(conclusion).append("</span></td>")
                .append("</tr></table>");

        // 一、车架档案
        h.append("<h2>一、车架档案</h2><table class=\"grid\">")
                .append(row2("车架序列号", esc(frame.getSerialNo()), "产品型号", esc(frame.getModel())))
                .append(row2("车架尺寸", esc(frame.getSize()), "生产车间", esc(frame.getWorkshop())))
                .append(row2("生产日期", frame.getProductionDate() == null ? "—" : frame.getProductionDate().format(DATE),
                        "当前状态", STATUS_TEXT.getOrDefault(frame.getStatus(), esc(frame.getStatus()))))
                .append("</table>");

        // 二、原料批次
        h.append("<h2>二、原材料批次</h2>");
        if (batch == null) {
            h.append("<p>该车架未关联原料批次。</p>");
        } else {
            h.append("<table class=\"grid\">")
                    .append(row2("批次号", esc(batch.getBatchNo()), "材料名称", esc(batch.getMaterialName())))
                    .append(row2("规格", esc(batch.getSpec()), "供应商", esc(batch.getSupplier())))
                    .append(row2("到货日期", batch.getIncomingDate() == null ? "—" : batch.getIncomingDate().format(DATE),
                            "来料检验", "PASSED".equals(batch.getQcStatus()) ? "合格" : esc(batch.getQcStatus())))
                    .append("</table>");
        }

        // 三、生产工序
        h.append("<h2>三、生产工序记录</h2>");
        if (processes.isEmpty()) {
            h.append("<p>暂无工序记录。</p>");
        } else {
            h.append("<table class=\"list\"><tr><th style=\"width:8%\">序号</th><th style=\"width:20%\">工序</th>")
                    .append("<th style=\"width:34%\">工艺参数</th><th style=\"width:18%\">操作人</th><th>记录时间</th></tr>");
            int i = 1;
            for (ProcessRecord p : processes) {
                h.append("<tr><td style=\"text-align:center\">").append(i++).append("</td>")
                        .append("<td>").append(PROCESS_TEXT.getOrDefault(p.getProcessType(), esc(p.getProcessType()))).append("</td>")
                        .append("<td class=\"mono\">").append(esc(formatParams(p.getRecordData()))).append("</td>")
                        .append("<td>").append(esc(userName(p.getOperatorId()))).append("</td>")
                        .append("<td>").append(p.getRecordTime() == null ? "—" : p.getRecordTime().format(DATETIME)).append("</td></tr>");
            }
            h.append("</table>");
        }

        // 四、质量检验
        h.append("<h2>四、质量检验记录</h2>");
        if (inspections.isEmpty()) {
            h.append("<p>暂无检验记录。</p>");
        } else {
            h.append("<table class=\"list\"><tr><th style=\"width:8%\">序号</th><th style=\"width:20%\">检验项目</th>")
                    .append("<th style=\"width:12%\">结果</th><th style=\"width:30%\">缺陷描述</th>")
                    .append("<th style=\"width:15%\">检验员</th><th>检验时间</th></tr>");
            int i = 1;
            for (InspectionRecord ins : inspections) {
                boolean pass = "PASS".equals(ins.getResult());
                h.append("<tr><td style=\"text-align:center\">").append(i++).append("</td>")
                        .append("<td>").append(ITEM_TEXT.getOrDefault(ins.getItem(), esc(ins.getItem()))).append("</td>")
                        .append("<td class=\"pass\" style=\"text-align:center\">").append(pass ? "合格" : "不合格").append("</td>")
                        .append("<td>").append(ins.getDefectDesc() == null || ins.getDefectDesc().isBlank() ? "—" : esc(ins.getDefectDesc())).append("</td>")
                        .append("<td>").append(esc(userName(ins.getInspectorId()))).append("</td>")
                        .append("<td>").append(ins.getInspectTime() == null ? "—" : ins.getInspectTime().format(DATETIME)).append("</td></tr>");
            }
            h.append("</table>");
        }

        // 五、区块链存证
        h.append("<h2>五、区块链存证摘要</h2><table class=\"grid\">")
                .append(row2("存储网络", "FISCO BCOS 联盟链（群组 group0）",
                        "哈希口径", "v" + (frame.getHashVersion() == null ? 1 : frame.getHashVersion())
                                + "（含车架本体、生产工序、质量检验"
                                + ((frame.getHashVersion() == null ? 1 : frame.getHashVersion()) >= 2 ? "、原料批次" : "") + "）"))
                .append(row2("链下实时哈希", "<span class=\"mono\">" + esc(str(verify.get("offChainHash"))) + "</span>",
                        "链上存证哈希", "<span class=\"mono\">" + esc(str(verify.get("onChainHash"))) + "</span>"))
                .append(row2("验真结论", verifyText(str(verify.get("result"))),
                        "存证状态", Boolean.TRUE.equals(verify.get("recorded")) ? "已上链" : "未上链"))
                .append("</table>");

        h.append("<div class=\"verdict\">检验结论：").append(conclusion).append("</div>");

        // 签字栏
        h.append("<table class=\"sign\"><tr>")
                .append("<td>检验员签字：________________</td>")
                .append("<td>质量负责人签字：________________</td>")
                .append("</tr><tr>")
                .append("<td>日期：______年____月____日</td>")
                .append("<td>日期：______年____月____日</td>")
                .append("</tr></table>");

        h.append("<div class=\"foot\">本报告由公路自行车碳纤维车架质量溯源系统自动生成，关键质量数据的 SHA-256 摘要已写入 FISCO BCOS 联盟链，可独立验证。</div>");
        h.append("</div></body></html>");
        return h.toString();
    }

    private String row2(String k1, String v1, String k2, String v2) {
        return "<tr><th>" + k1 + "</th><td>" + v1 + "</td><th>" + k2 + "</th><td>" + v2 + "</td></tr>";
    }

    private String verifyText(String result) {
        return switch (result) {
            case "VALID" -> "<span class=\"pass\">哈希一致，数据完整未被篡改</span>";
            case "TAMPERED" -> "<span class=\"fail\">哈希不一致，数据存在被篡改风险</span>";
            case "NOT_RECORDED" -> "尚未上链存证";
            case "CHAIN_UNREACHABLE" -> "区块链节点不可达，未能验真";
            default -> esc(result);
        };
    }

    private String userName(Long id) {
        if (id == null) {
            return "—";
        }
        User u = userMapper.selectById(id);
        return u == null ? String.valueOf(id) : (u.getRealName() == null ? u.getUsername() : u.getRealName());
    }

    private String str(Object o) {
        return o == null ? "—" : String.valueOf(o);
    }

    /**
     * 把工序参数 JSON 转成可读文本，例如
     * {"note":"LAYUP ok","temp":180} → "工艺状态正常；温度 180 ℃"。
     * 解析失败时原样返回，保证报告不会因为一条脏数据整体打不开。
     */
    private String formatParams(String raw) {
        if (raw == null || raw.isBlank()) {
            return "—";
        }
        try {
            JsonNode node = JSON.readTree(raw);
            if (!node.isObject() || node.isEmpty()) {
                return raw;
            }
            StringBuilder sb = new StringBuilder();
            node.fields().forEachRemaining(e -> {
                String k = e.getKey();
                String v = e.getValue().asText("");
                if (sb.length() > 0) {
                    sb.append("；");
                }
                switch (k) {
                    case "note" -> sb.append(v.toLowerCase().contains("ok") ? "工艺状态正常" : "备注 " + v);
                    case "temp", "temperature" -> sb.append("温度 ").append(v).append(" ℃");
                    case "pressure" -> sb.append("压力 ").append(v).append(" MPa");
                    case "duration", "time" -> sb.append("时长 ").append(v).append(" min");
                    case "layup" -> sb.append("铺层角度 ").append(v);
                    case "cure" -> sb.append("固化曲线 ").append(v);
                    default -> sb.append(k).append(" ").append(v);
                }
            });
            return sb.length() == 0 ? "—" : sb.toString();
        } catch (Exception ex) {
            return raw;
        }
    }

    /** 最小化 HTML 转义，防止库里的数据带尖括号把版式打乱 */
    private String esc(String s) {
        if (s == null) {
            return "—";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private String errorPage(String msg) {
        return "<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\"><title>报告生成失败</title></head>"
                + "<body style=\"font-family:SimSun;padding:40px\"><h2>报告生成失败</h2><p>" + esc(msg) + "</p></body></html>";
    }
}
