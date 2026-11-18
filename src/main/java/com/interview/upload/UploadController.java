package com.interview.upload;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.interview.auth.CurrentUser;
import com.interview.common.web.ListResponse;

import lombok.RequiredArgsConstructor;

/** API 명세 2.3 면접자 업로드. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class UploadController {

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final UploadService uploadService;
    private final SampleExcelGenerator sampleExcelGenerator;

    @PostMapping(value = "/recruitments/{id}/uploads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public UploadBatchDto upload(@PathVariable Long id, @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal CurrentUser user) {
        return uploadService.upload(id, file, user);
    }

    @GetMapping("/recruitments/{id}/uploads")
    @PreAuthorize("hasRole('ADMIN')")
    public ListResponse<UploadBatchDto> history(@PathVariable Long id) {
        return ListResponse.of(uploadService.history(id));
    }

    @GetMapping("/uploads/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public UploadBatchDto detail(@PathVariable Long id) {
        return uploadService.detail(id);
    }

    /**
     * 샘플 엑셀. 기본은 가짜 면접자 50행(일부러 넣은 오류 행 3개 포함)이라 그대로 올려 보면 성공·오류 표시를 모두 볼 수 있다.
     * empty=true 면 헤더만 있는 빈 양식을 준다.
     */
    @GetMapping("/uploads/template")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> template(@RequestParam(required = false) Boolean empty)
            throws UnsupportedEncodingException {
        boolean headerOnly = Boolean.TRUE.equals(empty);
        byte[] body = headerOnly ? sampleExcelGenerator.emptyTemplate() : sampleExcelGenerator.sample();
        String fileName = headerOnly ? "면접대상자_양식.xlsx" : "면접대상자_샘플.xlsx";
        String encoded = URLEncoder.encode(fileName, "UTF-8").replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"candidates-sample.xlsx\"; filename*=UTF-8''" + encoded)
                .body(body);
    }
}
