package com.tutor.app.controller;

import com.tutor.app.model.SessionStatus;
import com.tutor.app.model.Student;
import com.tutor.app.model.Tutor;
import com.tutor.app.model.TutoringSession;
import com.tutor.app.repository.StudentRepository;
import com.tutor.app.repository.TutorRepository;
import com.tutor.app.repository.TutoringSessionRepository;
import com.tutor.app.service.BookingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final TutorRepository tutorRepository;
    private final StudentRepository studentRepository;
    private final TutoringSessionRepository sessionRepository;
    private final BookingService bookingService;

    public ApiController(
            TutorRepository tutorRepository,
            StudentRepository studentRepository,
            TutoringSessionRepository sessionRepository,
            BookingService bookingService
    ) {
        this.tutorRepository = tutorRepository;
        this.studentRepository = studentRepository;
        this.sessionRepository = sessionRepository;
        this.bookingService = bookingService;
    }

    @GetMapping("/tutors")
    public List<Tutor> tutors() {
        return tutorRepository.findAll();
    }

    @GetMapping("/students")
    public List<Student> students() {
        return studentRepository.findAll();
    }

    @GetMapping("/sessions")
    public List<TutoringSession> sessions() {
        return sessionRepository.findAll();
    }

    @GetMapping("/sessions/scheduled")
    public List<TutoringSession> scheduledSessions() {
        return sessionRepository.findByStatus(SessionStatus.SCHEDULED);
    }

    @PostMapping("/sessions")
    public ResponseEntity<?> book(@Valid @RequestBody BookSessionApiRequest request) {
        try {
            TutoringSession created = bookingService.bookSession(request.toServiceRequest());
            return ResponseEntity.created(URI.create("/api/sessions/" + created.getId())).body(created);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/sessions/{id}/cancel")
    public ResponseEntity<?> cancel(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(bookingService.cancelSession(id));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    public record BookSessionApiRequest(
            @NotNull Long tutorId,
            @NotNull Long studentId,
            @NotNull com.tutor.app.model.Subject subject,
            @NotNull java.time.LocalDateTime startsAt,
            int durationMinutes,
            String notes
    ) {
        public BookingService.BookSessionRequest toServiceRequest() {
            return new BookingService.BookSessionRequest(
                    tutorId,
                    studentId,
                    subject,
                    startsAt,
                    durationMinutes,
                    notes
            );
        }
    }
}
