package net.k2ai.interviewSimulator.repository;

import net.k2ai.interviewSimulator.entity.InterviewSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface InterviewSessionRepository extends JpaRepository<InterviewSession, UUID> {

}// InterviewSessionRepository
