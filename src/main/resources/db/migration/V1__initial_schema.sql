-- V1__initial_schema.sql
-- Initial database schema for Interview Simulator

CREATE TABLE IF NOT EXISTS interview_sessions (
    id UUID PRIMARY KEY,
    candidate_name VARCHAR(255) NOT NULL,
    job_position VARCHAR(255) NOT NULL,
    difficulty VARCHAR(50) NOT NULL,
    started_at TIMESTAMP NOT NULL,
    ended_at TIMESTAMP,
    transcript TEXT,
    score INTEGER,
    feedback_json TEXT
);

CREATE INDEX IF NOT EXISTS idx_sessions_candidate ON interview_sessions(candidate_name);
CREATE INDEX IF NOT EXISTS idx_sessions_started_at ON interview_sessions(started_at);

CREATE TABLE IF NOT EXISTS interview_feedback (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES interview_sessions(id),
    overall_score INTEGER NOT NULL,
    communication_score INTEGER NOT NULL,
    technical_score INTEGER NOT NULL,
    confidence_score INTEGER NOT NULL,
    strengths TEXT,
    improvements TEXT,
    detailed_analysis TEXT,
    verdict VARCHAR(50),
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_feedback_session ON interview_feedback(session_id);
