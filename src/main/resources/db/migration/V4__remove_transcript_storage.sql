-- Remove transcript-level persistence for privacy-first retention.
-- Interview transcripts must stay in-memory only and never be stored in DB.

ALTER TABLE interview_sessions DROP COLUMN IF EXISTS transcript;
ALTER TABLE interview_sessions DROP COLUMN IF EXISTS feedback_json;
