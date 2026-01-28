package net.k2ai.interviewSimulator.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Service
public class InterviewPromptService {

    // Patterns to detect when AI is concluding the interview
    private static final List<Pattern> CONCLUSION_PATTERNS = List.of(
            Pattern.compile("thank you for your time", Pattern.CASE_INSENSITIVE),
            Pattern.compile("that concludes our interview", Pattern.CASE_INSENSITIVE),
            Pattern.compile("we have all the information we need", Pattern.CASE_INSENSITIVE),
            Pattern.compile("thank you for coming in", Pattern.CASE_INSENSITIVE),
            Pattern.compile("we('ll| will) be in touch", Pattern.CASE_INSENSITIVE),
            Pattern.compile("this concludes", Pattern.CASE_INSENSITIVE),
            Pattern.compile("end of (the|our) interview", Pattern.CASE_INSENSITIVE),
            Pattern.compile("that('s| is) all (the questions |)I have", Pattern.CASE_INSENSITIVE)
    );


    public String generateInterviewerPrompt(String position, String difficulty) {
        String difficultyBehavior = getDifficultyBehavior(difficulty);
        String positionContext = getPositionContext(position);

        return String.format("""
                You are an experienced HR interviewer conducting a job interview for a %s position.
                
                ## Your Role
                You are a professional interviewer at a reputable tech company. Your name is Alex.
                You should sound natural, professional, and human-like in your responses.
                
                ## Interview Guidelines
                1. Start by briefly introducing yourself and asking the candidate to introduce themselves
                2. Ask 5-7 relevant questions appropriate for a %s role
                3. Listen carefully to responses and ask follow-up questions when needed
                4. Keep your responses concise - this is a conversation, not a lecture
                5. Be professional but conversational
                
                ## Difficulty Level: %s
                %s
                
                ## Position-Specific Focus
                %s
                
                ## Concluding the Interview
                When you have gathered enough information (after 5-7 questions), naturally conclude by:
                - Thanking the candidate for their time
                - Mentioning that "we have all the information we need"
                - Saying something like "we'll be in touch with next steps"
                
                ## Important Notes
                - Do NOT mention that you are an AI - you are Alex, the interviewer
                - Keep responses SHORT and natural - avoid long monologues
                - React naturally to the candidate's answers
                - If the candidate gives a poor answer, probe deeper but remain professional
                - If the candidate is clearly struggling, you may offer gentle encouragement
                
                Begin the interview now by introducing yourself briefly.
                """,
                position, position, difficulty, difficultyBehavior, positionContext
        );
    }//generateInterviewerPrompt


    private String getDifficultyBehavior(String difficulty) {
        return switch (difficulty.toLowerCase()) {
            case "easy", "chill" -> """
                    - Be friendly, encouraging, and supportive
                    - Allow the candidate time to think
                    - If they struggle, offer hints or rephrase questions
                    - Focus on making them comfortable
                    - Ask straightforward questions without tricks
                    """;
            case "hard", "stress" -> """
                    - Be professional but challenging
                    - Ask probing follow-up questions
                    - Press for specific examples and details
                    - Challenge vague or incomplete answers
                    - Include some curveball or scenario-based questions
                    - Maintain time pressure in your tone
                    """;
            default -> """
                    - Be professional and balanced
                    - Ask clear, direct questions
                    - Follow up on interesting points
                    - Maintain a neutral but friendly tone
                    - Mix easy and moderately challenging questions
                    """;
        };
    }//getDifficultyBehavior


    private String getPositionContext(String position) {
        String lowerPosition = position.toLowerCase();

        if (lowerPosition.contains("java") || lowerPosition.contains("backend") || lowerPosition.contains("software")) {
            return """
                    Focus areas for this technical role:
                    - Object-oriented programming concepts
                    - Java/Spring Boot knowledge (if applicable)
                    - Database and SQL understanding
                    - API design and REST principles
                    - Problem-solving approach
                    - Code quality and testing practices
                    """;
        } else if (lowerPosition.contains("qa") || lowerPosition.contains("test") || lowerPosition.contains("quality")) {
            return """
                    Focus areas for this QA role:
                    - Testing methodologies and strategies
                    - Test case design and execution
                    - Bug reporting and tracking
                    - Automation experience
                    - Understanding of SDLC
                    - Attention to detail examples
                    """;
        } else if (lowerPosition.contains("project") || lowerPosition.contains("manager") || lowerPosition.contains("pm")) {
            return """
                    Focus areas for this management role:
                    - Project planning and execution
                    - Team leadership and communication
                    - Stakeholder management
                    - Risk identification and mitigation
                    - Agile/Scrum experience
                    - Conflict resolution examples
                    """;
        } else if (lowerPosition.contains("frontend") || lowerPosition.contains("ui") || lowerPosition.contains("react")) {
            return """
                    Focus areas for this frontend role:
                    - HTML, CSS, JavaScript proficiency
                    - Modern framework experience (React, Vue, Angular)
                    - Responsive design principles
                    - Browser compatibility handling
                    - Performance optimization
                    - User experience sensibility
                    """;
        } else if (lowerPosition.contains("devops") || lowerPosition.contains("cloud") || lowerPosition.contains("infrastructure")) {
            return """
                    Focus areas for this DevOps role:
                    - CI/CD pipeline experience
                    - Cloud platforms (AWS, GCP, Azure)
                    - Containerization (Docker, Kubernetes)
                    - Infrastructure as Code
                    - Monitoring and logging
                    - Security best practices
                    """;
        } else {
            return """
                    Focus areas for this role:
                    - Relevant technical skills and experience
                    - Problem-solving capabilities
                    - Communication skills
                    - Team collaboration
                    - Learning and adaptability
                    - Career goals and motivation
                    """;
        }
    }//getPositionContext


    public boolean isInterviewConcluding(String transcript) {
        if (transcript == null || transcript.isBlank()) {
            return false;
        }

        for (Pattern pattern : CONCLUSION_PATTERNS) {
            if (pattern.matcher(transcript).find()) {
                log.debug("Detected interview conclusion pattern in: {}", transcript);
                return true;
            }
        }

        return false;
    }//isInterviewConcluding

}//InterviewPromptService
