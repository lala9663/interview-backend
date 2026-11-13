package com.interview.upload;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** excel-mapping.yml 을 읽은 모양. */
@Getter
@Setter
public class ExcelMapping {

    private int sheetIndex = 0;
    private int headerRow = 1;
    private List<Column> columns = new ArrayList<Column>();

    @Getter
    @Setter
    public static class Column {
        private String header;
        private List<String> aliases = new ArrayList<String>();
        /** 저장할 곳. blind 컬럼은 비워 둔다. */
        private String field;
        private boolean required;
        private boolean blind;
        /** blind 컬럼의 이름표(blindedFields 에 남는 값). */
        private String blindKey;
        /** essay / project / career 의 제목. */
        private String title;
        private Integer maxLength;
    }

    /** 헤더 비교용: 공백을 모두 없앤다. */
    public static String normalize(String header) {
        return header == null ? "" : header.replaceAll("\\s+", "");
    }

    /** 헤더 글자에 맞는 컬럼 정의. 없으면 null. */
    public Column find(String headerText) {
        String key = normalize(headerText);
        if (key.isEmpty()) {
            return null;
        }
        for (Column c : columns) {
            if (normalize(c.getHeader()).equals(key)) {
                return c;
            }
            for (String alias : c.getAliases()) {
                if (normalize(alias).equals(key)) {
                    return c;
                }
            }
        }
        return null;
    }
}
