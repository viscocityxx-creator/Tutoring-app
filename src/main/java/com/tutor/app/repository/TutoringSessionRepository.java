package com.tutor.app.repository;

import com.tutor.app.model.SessionStatus;
import com.tutor.app.model.TutoringSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TutoringSessionRepository extends JpaRepository<TutoringSession, Long> {
    List<TutoringSession> findByTutorIdAndStatusNot(Long tutorId, SessionStatus status);
    List<TutoringSession> findByStudentIdAndStatusNot(Long studentId, SessionStatus status);
    List<TutoringSession> findByStatus(SessionStatus status);
}
