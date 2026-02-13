package net.k2ai.interviewSimulator.repository;

import net.k2ai.interviewSimulator.entity.InterviewSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface InterviewSessionRepository extends JpaRepository<InterviewSession, UUID> {

	List<InterviewSession> findByStartedAtAfterOrderByStartedAtDesc(LocalDateTime cutoff);


	List<InterviewSession> findByStartedAtBefore(LocalDateTime cutoff);

}//InterviewSessionRepository
