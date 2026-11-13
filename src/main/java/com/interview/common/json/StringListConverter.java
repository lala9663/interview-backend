package com.interview.common.json;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

/** 짧은 문자열 목록(직무 코드, 블라인드 항목 키)을 쉼표로 이어 VARCHAR 한 컬럼에 저장한다. */
@Converter
public class StringListConverter implements AttributeConverter<List<String>, String> {

    @Override
    public String convertToDatabaseColumn(List<String> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (String s : attribute) {
            if (s == null || s.trim().isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(s.trim());
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        List<String> result = new ArrayList<String>();
        if (dbData == null || dbData.trim().isEmpty()) {
            return result;
        }
        for (String s : dbData.split(",")) {
            if (!s.trim().isEmpty()) {
                result.add(s.trim());
            }
        }
        return result;
    }
}
