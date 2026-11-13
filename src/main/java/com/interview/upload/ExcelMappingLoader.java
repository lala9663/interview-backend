package com.interview.upload;

import java.io.IOException;
import java.io.InputStream;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import lombok.RequiredArgsConstructor;

/**
 * 시작할 때 엑셀 매핑 파일을 읽는다. 위치는 app.upload.mapping-location
 * (기본 classpath:excel-mapping.yml, 운영에서는 file:/경로/excel-mapping.yml 로 바꿔 재배포 없이 고칠 수 있다).
 */
@Component
@RequiredArgsConstructor
public class ExcelMappingLoader {

    private final ResourceLoader resourceLoader;

    @Value("${app.upload.mapping-location:classpath:excel-mapping.yml}")
    private String location;

    private ExcelMapping mapping;

    @PostConstruct
    public void load() {
        Resource resource = resourceLoader.getResource(location);
        ObjectMapper yaml = new ObjectMapper(new YAMLFactory())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        try (InputStream in = resource.getInputStream()) {
            ExcelMapping loaded = yaml.readValue(in, ExcelMapping.class);
            validate(loaded);
            this.mapping = loaded;
        } catch (IOException e) {
            throw new IllegalStateException("엑셀 매핑 파일을 읽을 수 없습니다: " + location, e);
        }
    }

    public ExcelMapping get() {
        return mapping;
    }

    private void validate(ExcelMapping m) {
        for (ExcelMapping.Column c : m.getColumns()) {
            if (c.getHeader() == null || c.getHeader().trim().isEmpty()) {
                throw new IllegalStateException("엑셀 매핑: header 가 빈 컬럼이 있습니다.");
            }
            if (c.isBlind()) {
                if (c.getBlindKey() == null || c.getField() != null) {
                    throw new IllegalStateException("엑셀 매핑: blind 컬럼은 blindKey 만 가져야 합니다: " + c.getHeader());
                }
            } else if (c.getField() == null) {
                throw new IllegalStateException("엑셀 매핑: field 가 없습니다: " + c.getHeader());
            }
        }
        for (String required : new String[] { "applicantNo", "name", "jobCode" }) {
            boolean found = false;
            for (ExcelMapping.Column c : m.getColumns()) {
                if (required.equals(c.getField())) {
                    found = true;
                }
            }
            if (!found) {
                throw new IllegalStateException("엑셀 매핑: " + required + " 컬럼 정의가 필요합니다.");
            }
        }
    }
}
