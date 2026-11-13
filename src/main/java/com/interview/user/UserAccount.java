package com.interview.user;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

import com.interview.common.json.StringListConverter;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 사용자(면접관·관리자). USER 는 Oracle 예약어라 테이블 이름은 IV_USER_ACCOUNT. */
@Entity
@Table(name = "IV_USER_ACCOUNT", uniqueConstraints = @UniqueConstraint(name = "UK_IV_USER_EMP_NO", columnNames = "EMPLOYEE_NO"))
@Getter
@Setter
@NoArgsConstructor
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ivUserAccountSeq")
    @SequenceGenerator(name = "ivUserAccountSeq", sequenceName = "IV_USER_ACCOUNT_SEQ", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "EMPLOYEE_NO", nullable = false, length = 30)
    private String employeeNo;

    @Column(name = "NAME", nullable = false, length = 100)
    private String name;

    @Column(name = "DEPARTMENT", length = 100)
    private String department;

    @Enumerated(EnumType.STRING)
    @Column(name = "ROLE_CODE", nullable = false, length = 20)
    private UserRole role;

    /** 담당 가능한 직무 코드. JD 기반 매칭에 쓴다. */
    @Convert(converter = StringListConverter.class)
    @Column(name = "JOB_CODES", length = 500)
    private List<String> jobCodes = new ArrayList<String>();

    @Column(name = "IS_ACTIVE", nullable = false)
    private boolean active = true;
}
