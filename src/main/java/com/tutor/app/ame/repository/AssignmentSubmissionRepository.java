package com.tutor.app.ame.repository;

import com.tutor.app.ame.model.AssignmentStatus;
import com.tutor.app.ame.model.AssignmentSubmission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssignmentSubmissionRepository extends JpaRepository<AssignmentSubmission, Long> {
    List<AssignmentSubmission> findByUserEmailIgnoreCaseOrderByCreatedAtDesc(String userEmail);
    List<AssignmentSubmission> findByStatusOrderByExpectedDeliveryUtcAsc(AssignmentStatus status);
}
