package com.interview.persona;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonaRepository extends JpaRepository<Persona, Long> {

    /** 전체 목록, 만든 순서. */
    List<Persona> findAllByOrderByIdAsc();

    List<Persona> findByName(String name);

    long countByEnabledTrue();
}
