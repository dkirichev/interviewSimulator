-- Add language column to interview_sessions
ALTER TABLE interview_sessions ADD COLUMN language VARCHAR(10) DEFAULT 'en';
