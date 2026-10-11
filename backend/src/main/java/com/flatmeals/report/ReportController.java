package com.flatmeals.report;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 *   GET /api/months/{y}/{m}/report.csv   MANAGER   the whole month as a spreadsheet file
 *
 * Manager only, because it lists every member's dues and payments.
 */
@RestController
public class ReportController {

    static final MediaType CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final MonthReportService reportService;

    public ReportController(MonthReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/api/months/{year}/{month}/report.csv")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<byte[]> csv(@PathVariable("year") int year, @PathVariable("month") int month) {
        byte[] body = MonthReportCsv.bytes(reportService.build(year, month));
        return ResponseEntity.ok()
                .contentType(CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(MonthReportCsv.fileName(year, month)).build().toString())
                .body(body);
    }
}
