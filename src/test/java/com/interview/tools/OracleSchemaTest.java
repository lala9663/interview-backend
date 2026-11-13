package com.interview.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.hibernate.boot.Metadata;
import org.hibernate.boot.model.relational.Namespace;
import org.hibernate.boot.model.relational.Sequence;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.Index;
import org.hibernate.mapping.Table;
import org.hibernate.mapping.UniqueKey;
import org.junit.jupiter.api.Test;

/** Oracle 로 옮길 때 깨지는 것(이름 길이, 예약어, 접두사)과 schema.sql 의 최신 여부를 검사한다. */
class OracleSchemaTest {

    private static final int MAX_IDENTIFIER = 30;

    /** 컬럼·테이블 이름으로 쓰면 안 되는 Oracle 예약어(와 문제를 일으키는 키워드). */
    private static final Set<String> RESERVED = new HashSet<String>(Arrays.asList("ACCESS", "ADD", "ALL", "ALTER", "AND",
            "ANY", "AS", "ASC", "AUDIT", "BETWEEN", "BY", "CHAR", "CHECK", "CLUSTER", "COLUMN", "COMMENT", "COMPRESS",
            "CONNECT", "CREATE", "CURRENT", "DATE", "DECIMAL", "DEFAULT", "DELETE", "DESC", "DISTINCT", "DROP", "ELSE",
            "EXCLUSIVE", "EXISTS", "FILE", "FLOAT", "FOR", "FROM", "GRANT", "GROUP", "HAVING", "IDENTIFIED", "IMMEDIATE",
            "IN", "INCREMENT", "INDEX", "INITIAL", "INSERT", "INTEGER", "INTERSECT", "INTO", "IS", "LEVEL", "LIKE", "LOCK",
            "LONG", "MAXEXTENTS", "MINUS", "MLSLABEL", "MODE", "MODIFY", "NOAUDIT", "NOCOMPRESS", "NOT", "NOWAIT", "NULL",
            "NUMBER", "OF", "OFFLINE", "ON", "ONLINE", "OPTION", "OR", "ORDER", "PCTFREE", "PRIOR", "PRIVILEGES", "PUBLIC",
            "RAW", "RENAME", "RESOURCE", "REVOKE", "ROW", "ROWID", "ROWNUM", "ROWS", "SELECT", "SESSION", "SET", "SHARE",
            "SIZE", "SMALLINT", "START", "SUCCESSFUL", "SYNONYM", "SYSDATE", "TABLE", "THEN", "TO", "TRIGGER", "UID",
            "UNION", "UNIQUE", "UPDATE", "USER", "VALIDATE", "VALUES", "VARCHAR", "VARCHAR2", "VIEW", "WHENEVER", "WHERE",
            "WITH", "TYPE", "TIMESTAMP", "VALUE", "KEY", "TEXT"));

    @Test
    void identifiersAreSafeForOracle() {
        StandardServiceRegistry registry = OracleSchemaGenerator.registry();
        try {
            Metadata metadata = OracleSchemaGenerator.metadata(registry);
            List<String> problems = new ArrayList<String>();
            int tableCount = 0;
            for (Table table : metadata.collectTableMappings()) {
                tableCount++;
                String tableName = table.getName().toUpperCase();
                if (!tableName.startsWith("IV_")) {
                    problems.add("테이블 접두사 IV_ 없음: " + tableName);
                }
                check(problems, "테이블", tableName);
                Iterator<Column> columns = table.getColumnIterator();
                while (columns.hasNext()) {
                    Column column = columns.next();
                    check(problems, tableName + " 컬럼", column.getName().toUpperCase());
                    // 한글은 UTF-8 로 3바이트라 1000자를 넘으면 Oracle VARCHAR2 한도(4000바이트)를 넘을 수 있다.
                    if (column.getSqlTypeCode(metadata) == java.sql.Types.VARCHAR && column.getLength() > 1000) {
                        problems.add(tableName + "." + column.getName() + " 길이가 1000 을 넘음(더 길면 @Lob)");
                    }
                }
                Iterator<Index> indexes = table.getIndexIterator();
                while (indexes.hasNext()) {
                    check(problems, tableName + " 인덱스", indexes.next().getName().toUpperCase());
                }
                Iterator<UniqueKey> uniqueKeys = table.getUniqueKeyIterator();
                while (uniqueKeys.hasNext()) {
                    check(problems, tableName + " 유니크", uniqueKeys.next().getName().toUpperCase());
                }
            }
            for (Namespace namespace : metadata.getDatabase().getNamespaces()) {
                for (Sequence sequence : namespace.getSequences()) {
                    check(problems, "시퀀스", sequence.getName().getSequenceName().getText().toUpperCase());
                }
            }
            assertThat(tableCount).isGreaterThan(15);
            assertThat(problems).as("Oracle 에서 문제가 되는 이름").isEmpty();
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    @Test
    void schemaFileIsUpToDate() throws Exception {
        File file = new File("db/oracle/schema.sql");
        assertThat(file).as("db/oracle/schema.sql 이 없습니다. ./gradlew generateOracleSchema 를 실행하세요.").exists();
        String onDisk = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).replace("\r\n", "\n");
        assertThat(onDisk)
                .as("엔티티가 바뀌었습니다. ./gradlew generateOracleSchema 를 실행해 db/oracle/schema.sql 을 다시 만드세요.")
                .isEqualTo(OracleSchemaGenerator.generate());
    }

    private static void check(List<String> problems, String kind, String name) {
        if (name.length() > MAX_IDENTIFIER) {
            problems.add(kind + " 이름이 30자를 넘음: " + name);
        }
        if (RESERVED.contains(name)) {
            problems.add(kind + " 이름이 예약어: " + name);
        }
    }
}
