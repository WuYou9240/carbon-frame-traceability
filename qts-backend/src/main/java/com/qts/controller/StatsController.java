package com.qts.controller;

import com.qts.common.Result;
import com.qts.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @GetMapping("/overview")
    @PreAuthorize("hasAnyRole('ADMIN','DEALER','QC')")
    public Result<Object> overview() {
        return Result.ok(statsService.overview());
    }

    /**
     * 导出台账 CSV（可直接用 Excel 打开）。
     * 文件名用日期+英文，避免中文文件名在部分浏览器下乱码。
     */
    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('ADMIN','QC')")
    public ResponseEntity<byte[]> export() {
        String csv = statsService.exportFrameCsv();
        String filename = "quality-ledger-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".csv";
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }
}
