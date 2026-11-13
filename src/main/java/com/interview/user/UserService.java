package com.interview.user;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.interview.common.ApiException;
import com.interview.common.Chunks;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserAccountRepository userRepository;

    public UserAccount get(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));
    }

    /** 면접관 후보: 활성 사용자 전체(ADMIN 도 면접관이 될 수 있다). jobCode 를 주면 그 직무를 담당하는 사람만. */
    public List<UserAccount> findInterviewers(String jobCode) {
        List<UserAccount> result = new ArrayList<UserAccount>();
        for (UserAccount u : userRepository.findByActiveTrueOrderByNameAsc()) {
            if (jobCode == null || jobCode.trim().isEmpty() || u.getJobCodes().contains(jobCode.trim())) {
                result.add(u);
            }
        }
        return result;
    }

    public Map<Long, UserAccount> mapByIds(Collection<Long> ids) {
        Map<Long, UserAccount> map = new HashMap<Long, UserAccount>();
        for (List<Long> chunk : Chunks.of(ids)) {
            for (UserAccount u : userRepository.findAllById(chunk)) {
                map.put(u.getId(), u);
            }
        }
        return map;
    }
}
