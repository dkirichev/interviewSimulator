-- Add user_token column for interview history tracking (Feature 1)
ALTER TABLE interview_sessions ADD COLUMN IF NOT EXISTS user_token VARCHAR(64);

-- Add topic_focus column for practice mode (Feature 2)
ALTER TABLE interview_sessions ADD COLUMN IF NOT EXISTS topic_focus VARCHAR(50);

-- Add interview_length column for custom interview length (Feature 7)
ALTER TABLE interview_sessions ADD COLUMN IF NOT EXISTS interview_length VARCHAR(20) DEFAULT 'standard';

-- Index on user_token for fast history lookups
CREATE INDEX IF NOT EXISTS idx_interview_sessions_user_token ON interview_sessions(user_token);
