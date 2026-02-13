package net.k2ai.interviewSimulator.repository;

import net.k2ai.interviewSimulator.entity.InterviewFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InterviewFeedbackRepository extends JpaRepository<InterviewFeedback, UUID> {

	Optional<InterviewFeedback> findBySessionId(UUID sessionId);


	List<InterviewFeedback> findBySessionStartedAtAfter(LocalDateTime cutoff);


	void deleteBySessionId(UUID sessionId);

}//InterviewFeedbackRepository
