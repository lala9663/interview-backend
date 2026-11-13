package com.interview.candidate;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

import com.interview.common.json.Json;

@Converter
public class CandidateProfileConverter implements AttributeConverter<CandidateProfile, String> {

    @Override
    public String convertToDatabaseColumn(CandidateProfile attribute) {
        return Json.write(attribute);
    }

    @Override
    public CandidateProfile convertToEntityAttribute(String dbData) {
        return Json.read(dbData, CandidateProfile.class);
    }
}
