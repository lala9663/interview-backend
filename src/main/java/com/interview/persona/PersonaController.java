package com.interview.persona;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.interview.common.web.ListResponse;

import lombok.RequiredArgsConstructor;

/** API 명세 2.12 면접 페르소나. 면접에 고르기(PUT /interviews/{id}/persona)는 interview.InterviewController 에 있다. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PersonaController {

    private final PersonaService personaService;

    @GetMapping("/personas")
    public ListResponse<PersonaDtos.PersonaDto> list() {
        List<PersonaDtos.PersonaDto> items = new ArrayList<PersonaDtos.PersonaDto>();
        for (Persona p : personaService.list()) {
            items.add(new PersonaDtos.PersonaDto(p));
        }
        return ListResponse.of(items);
    }

    @PostMapping("/admin/personas")
    @PreAuthorize("hasRole('ADMIN')")
    public PersonaDtos.PersonaDto create(@RequestBody PersonaDtos.SaveRequest request) {
        return new PersonaDtos.PersonaDto(personaService.create(request));
    }

    @PatchMapping("/admin/personas/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public PersonaDtos.PersonaDto patch(@PathVariable Long id, @RequestBody PersonaDtos.SaveRequest request) {
        return new PersonaDtos.PersonaDto(personaService.patch(id, request));
    }

    @DeleteMapping("/admin/personas/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        personaService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
