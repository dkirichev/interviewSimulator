package net.k2ai.interviewSimulator.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "interview_feedback")
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewFeedback {

	@Id
	@EqualsAndHashCode.Include
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "session_id", nullable = false)
	private InterviewSession session;

	@Column(nullable = false)
	private Integer overallScore;

	@Column(nullable = false)
	private Integer communicationScore;

	@Column(nullable = false)
	private Integer technicalScore;

	@Column(nullable = false)
	private Integer confidenceScore;

	@Column(columnDefinition = "TEXT")
	private String strengths;

	@Column(columnDefinition = "TEXT")
	private String improvements;

	@Column(columnDefinition = "TEXT")
	private String detailedAnalysis;

	@Column(length = 50)
	private String verdict;

	@Column(nullable = false)
	private LocalDateTime createdAt;


	@PrePersist
	protected void onCreate() {
		createdAt = LocalDateTime.now();
	}//onCreate

}//InterviewFeedback
