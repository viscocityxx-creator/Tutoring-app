package com.tutor.app.service;

import com.tutor.app.model.SessionStatus;
import com.tutor.app.model.Student;
import com.tutor.app.model.Subject;
import com.tutor.app.model.Tutor;
import com.tutor.app.model.TutoringSession;
import com.tutor.app.repository.StudentRepository;
import com.tutor.app.repository.TutorRepository;
import com.tutor.app.repository.TutoringSessionRepository;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class BookingService {

    private final TutorRepository tutorRepository;
    private final StudentRepository studentRepository;
    private final TutoringSessionRepository sessionRepository;

    public BookingService(
            TutorRepository tutorRepository,
            StudentRepository studentRepository,
            TutoringSessionRepository sessionRepository
    ) {
        this.tutorRepository = tutorRepository;
        this.studentRepository = studentRepository;
        this.sessionRepository = sessionRepository;
    }

    public TutoringSession bookSession(@NotNull BookSessionRequest request) {
        Tutor tutor = tutorRepository.findById(request.tutorId())
                .orElseThrow(() -> new IllegalArgumentException("Tutor not found"));
        Student student = studentRepository.findById(request.studentId())
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));

        if (!tutor.getSubjects().contains(request.subject())) {
            throw new IllegalArgumentException("Tutor does not teach " + request.subject());
        }
        if (request.durationMinutes() <= 0) {
            throw new IllegalArgumentException("Duration must be greater than 0");
        }

        List<TutoringSession> tutorSessions = sessionRepository
                .findByTutorIdAndStatusNot(tutor.getId(), SessionStatus.CANCELLED);
        validateNoOverlap(
                request.startsAt(),
                request.durationMinutes(),
                tutorSessions,
                "Tutor already has a session in this time window"
        );

        List<TutoringSession> studentSessions = sessionRepository
                .findByStudentIdAndStatusNot(student.getId(), SessionStatus.CANCELLED);
        validateNoOverlap(
                request.startsAt(),
                request.durationMinutes(),
                studentSessions,
                "Student already has a session in this time window"
        );

        TutoringSession session = new TutoringSession();
        session.setTutor(tutor);
        session.setStudent(student);
        session.setSubject(request.subject());
        session.setStartsAt(request.startsAt());
        session.setDurationMinutes(request.durationMinutes());
        session.setNotes(request.notes());
        session.setStatus(SessionStatus.SCHEDULED);
        return sessionRepository.save(session);
    }

    public TutoringSession cancelSession(Long sessionId) {
        TutoringSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));
        session.setStatus(SessionStatus.CANCELLED);
        return sessionRepository.save(session);
    }

    private void validateNoOverlap(
            LocalDateTime requestedStart,
            int requestedDuration,
            List<TutoringSession> existing,
            String errorMessage
    ) {
        LocalDateTime requestedEnd = requestedStart.plusMinutes(requestedDuration);
        boolean overlaps = existing.stream().anyMatch(s -> {
            LocalDateTime start = s.getStartsAt();
            LocalDateTime end = s.getStartsAt().plusMinutes(s.getDurationMinutes());
            return requestedStart.isBefore(end) && requestedEnd.isAfter(start);
        });

        if (overlaps) {
            throw new IllegalArgumentException(errorMessage);
        }
    }

    public record BookSessionRequest(
            Long tutorId,
            Long studentId,
            Subject subject,
            LocalDateTime startsAt,
            int durationMinutes,
            String notes
    ) {
    }
}
