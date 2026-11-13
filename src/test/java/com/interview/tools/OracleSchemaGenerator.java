package com.interview.tools;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.EnumSet;
import java.util.Set;
import java.util.TreeSet;

import javax.persistence.Entity;

import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.tool.hbm2ddl.SchemaExport;
import org.hibernate.tool.schema.TargetType;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.orm.jpa.hibernate.SpringImplicitNamingStrategy;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

/**
 * JPA 엔티티로부터 Oracle DDL 을 만든다. DB 연결이 필요 없다.
 *
 * 실행: ./gradlew generateOracleSchema   → db/oracle/schema.sql 을 다시 쓴다.
 * 엔티티를 바꾸면 반드시 다시 실행한다. 안 하면 OracleSchemaTest 가 실패한다.
 *
 * 이름 규칙은 운영과 같다(Spring Boot 기본: CamelCaseToUnderscoresNamingStrategy + SpringImplicitNamingStrategy).
 */
public final class OracleSchemaGenerator {

    public static final String DIALECT = "org.hibernate.dialect.Oracle12cDialect";

    private static final String HEADER = "-- 면접 지원 백엔드 Oracle 스키마.\n"
            + "-- 이 파일은 자동 생성된다. 직접 고치지 말고 엔티티를 고친 뒤 ./gradlew generateOracleSchema 를 실행한다.\n"
            + "-- 운영 DB 에 이미 테이블이 있으면 이 파일을 통째로 실행하지 말고, 바뀐 부분만 ALTER 문으로 옮긴다(docs/database.md).\n\n";

    private OracleSchemaGenerator() {
    }

    public static void main(String[] args) throws IOException {
        File out = new File(args.length > 0 ? args[0] : "db/oracle/schema.sql");
        if (out.getParentFile() != null) {
            out.getParentFile().mkdirs();
        }
        Files.write(out.toPath(), generate().getBytes(StandardCharsets.UTF_8));
        System.out.println("Oracle DDL 생성: " + out.getAbsolutePath());
    }

    /** 엔티티 클래스 이름(정렬됨). */
    public static Set<String> entityClassNames() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        Set<String> names = new TreeSet<String>();
        for (BeanDefinition bd : scanner.findCandidateComponents("com.interview")) {
            names.add(bd.getBeanClassName());
        }
        return names;
    }

    public static StandardServiceRegistry registry() {
        return new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", DIALECT)
                .applySetting("hibernate.temp.use_jdbc_metadata_defaults", "false")
                .build();
    }

    public static Metadata metadata(StandardServiceRegistry registry) {
        MetadataSources sources = new MetadataSources(registry);
        for (String name : entityClassNames()) {
            try {
                sources.addAnnotatedClass(Class.forName(name));
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }
        }
        return sources.getMetadataBuilder()
                .applyPhysicalNamingStrategy(new CamelCaseToUnderscoresNamingStrategy())
                .applyImplicitNamingStrategy(new SpringImplicitNamingStrategy())
                .build();
    }

    /** DDL 전체(머리말 포함). 줄바꿈은 \n. */
    public static String generate() throws IOException {
        StandardServiceRegistry registry = registry();
        try {
            Metadata metadata = metadata(registry);
            File temp = File.createTempFile("iv-oracle-schema", ".sql");
            try {
                Files.delete(temp.toPath());
                SchemaExport export = new SchemaExport();
                export.setFormat(true);
                export.setDelimiter(";");
                export.setOutputFile(temp.getAbsolutePath());
                export.createOnly(EnumSet.of(TargetType.SCRIPT), metadata);
                String ddl = new String(Files.readAllBytes(temp.toPath()), StandardCharsets.UTF_8);
                return HEADER + ddl.replace("\r\n", "\n").trim() + "\n";
            } finally {
                Files.deleteIfExists(temp.toPath());
            }
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }
}
