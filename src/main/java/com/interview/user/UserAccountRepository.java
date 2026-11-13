package com.interview.user;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByEmployeeNo(String employeeNo);

    List<UserAccount> findByActiveTrueOrderByNameAsc();
}
