package net.k2ai.interviewSimulator.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "interview_sessions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String candidateName;

    @Column(nullable = false)
    private String jobPosition;

    @Column(nullable = false)
    private String difficulty;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    private LocalDateTime endedAt;

    @Column(columnDefinition = "TEXT")
    private String transcript;

    private Integer score;

    @Column(columnDefinition = "TEXT")
    private String feedbackJson;

}//InterviewSession

