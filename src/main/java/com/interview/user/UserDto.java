package com.interview.user;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;

@Getter
public class UserDto {

    private final Long id;
    private final String employeeNo;
    private final String name;
    private final String department;
    private final UserRole role;
    private final List<String> jobCodes;

    private UserDto(UserAccount u) {
        this.id = u.getId();
        this.employeeNo = u.getEmployeeNo();
        this.name = u.getName();
        this.department = u.getDepartment();
        this.role = u.getRole();
        this.jobCodes = new ArrayList<String>(u.getJobCodes());
    }

    public static UserDto from(UserAccount u) {
        return new UserDto(u);
    }
}
