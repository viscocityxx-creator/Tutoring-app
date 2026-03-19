package com.tutor.app.service;

import com.tutor.app.model.SessionStatus;
import com.tutor.app.model.Student;
import com.tutor.app.model.Subject;
import com.tutor.app.model.Tutor;
import com.tutor.app.model.TutoringSession;
import com.tutor.app.repository.StudentRepository;
import com.tutor.app.repository.TutorRepository;
import com.tutor.app.repository.TutoringSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceTests {

    @Mock
    private TutorRepository tutorRepository;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private TutoringSessionRepository sessionRepository;

    @InjectMocks
    private BookingService bookingService;

    private Tutor tutor;
    private Student student;

    @BeforeEach
    void setUp() {
        tutor = new Tutor();
        tutor.setId(1L);
        tutor.setName("Tutor");
        tutor.setEmail("tutor@example.com");
        tutor.setBio("Bio");
        tutor.setHourlyRate(new BigDecimal("50.00"));
        tutor.setSubjects(Set.of(Subject.MATH, Subject.SCIENCE));

        student = new Student();
        student.setId(2L);
        student.setName("Student");
        student.setEmail("student@example.com");
        student.setGoal("Goal");
    }

    @Test
    void booksSessionWhenTutorAndStudentAreAvailable() {
        BookingService.BookSessionRequest request = new BookingService.BookSessionRequest(
                tutor.getId(),
                student.getId(),
                Subject.MATH,
                LocalDateTime.of(2026, 3, 20, 16, 0),
                60,
                "Algebra review"
        );

        when(tutorRepository.findById(tutor.getId())).thenReturn(Optional.of(tutor));
        when(studentRepository.findById(student.getId())).thenReturn(Optional.of(student));
        when(sessionRepository.findByTutorIdAndStatusNot(tutor.getId(), SessionStatus.CANCELLED))
                .thenReturn(List.of());
        when(sessionRepository.findByStudentIdAndStatusNot(student.getId(), SessionStatus.CANCELLED))
                .thenReturn(List.of());
        when(sessionRepository.save(any(TutoringSession.class))).thenAnswer(invocation -> {
            TutoringSession s = invocation.getArgument(0);
            s.setId(100L);
            return s;
        });

        TutoringSession created = bookingService.bookSession(request);

        assertEquals(100L, created.getId());
        assertEquals(SessionStatus.SCHEDULED, created.getStatus());
        assertEquals(tutor.getId(), created.getTutor().getId());
        assertEquals(student.getId(), created.getStudent().getId());
    }

    @Test
    void rejectsBookingWhenStudentAlreadyHasOverlappingSession() {
        BookingService.BookSessionRequest request = new BookingService.BookSessionRequest(
                tutor.getId(),
                student.getId(),
                Subject.MATH,
                LocalDateTime.of(2026, 3, 20, 16, 30),
                60,
                "Geometry prep"
        );

        TutoringSession existingForStudent = new TutoringSession();
        existingForStudent.setStudent(student);
        existingForStudent.setTutor(tutor);
        existingForStudent.setSubject(Subject.MATH);
        existingForStudent.setStartsAt(LocalDateTime.of(2026, 3, 20, 16, 0));
        existingForStudent.setDurationMinutes(60);
        existingForStudent.setStatus(SessionStatus.SCHEDULED);

        when(tutorRepository.findById(tutor.getId())).thenReturn(Optional.of(tutor));
        when(studentRepository.findById(student.getId())).thenReturn(Optional.of(student));
        when(sessionRepository.findByTutorIdAndStatusNot(tutor.getId(), SessionStatus.CANCELLED))
                .thenReturn(List.of());
        when(sessionRepository.findByStudentIdAndStatusNot(student.getId(), SessionStatus.CANCELLED))
                .thenReturn(List.of(existingForStudent));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> bookingService.bookSession(request)
        );

        assertEquals("Student already has a session in this time window", ex.getMessage());
    }

    @Test
    void cancelsExistingSession() {
        TutoringSession existing = new TutoringSession();
        existing.setId(50L);
        existing.setStatus(SessionStatus.SCHEDULED);

        when(sessionRepository.findById(50L)).thenReturn(Optional.of(existing));
        when(sessionRepository.save(existing)).thenReturn(existing);

        TutoringSession result = bookingService.cancelSession(50L);

        assertEquals(SessionStatus.CANCELLED, result.getStatus());
        verify(sessionRepository).save(existing);
    }
}
