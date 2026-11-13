package com.interview.recruitment;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RecruitmentRepository extends JpaRepository<Recruitment, Long> {

    List<Recruitment> findAllByOrderByIdAsc();

    List<Recruitment> findByTrackOrderByIdAsc(Track track);
}
