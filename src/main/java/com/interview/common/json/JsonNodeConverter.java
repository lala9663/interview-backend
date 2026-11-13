package com.interview.common.json;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

import com.fasterxml.jackson.databind.JsonNode;

/** JSON 모양 데이터를 CLOB(JSON 문자열)로 저장한다. 엔티티 필드에 @Lob @Convert 로 붙인다. */
@Converter
public class JsonNodeConverter implements AttributeConverter<JsonNode, String> {

    @Override
    public String convertToDatabaseColumn(JsonNode attribute) {
        return attribute == null || attribute.isNull() ? null : Json.write(attribute);
    }

    @Override
    public JsonNode convertToEntityAttribute(String dbData) {
        return Json.tree(dbData);
    }
}
