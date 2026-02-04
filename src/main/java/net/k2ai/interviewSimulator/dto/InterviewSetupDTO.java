package net.k2ai.interviewSimulator.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.io.Serializable;

/**
 * DTO for multi-step interview setup form.
 * Stored in HTTP session between wizard steps.
 */
@Data
public class InterviewSetupDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    // Step 1: Profile
    @NotBlank(message = "{validation.name.required}")
    @Size(min = 2, max = 100, message = "{validation.name.size}")
    private String candidateName;

    // Step 2: Details
    @NotBlank(message = "{validation.position.required}")
    private String position;

    private String customPosition;

    @NotBlank(message = "{validation.difficulty.required}")
    private String difficulty = "Easy";

    // CV - transient (not serializable), extracted text stored instead
    private transient MultipartFile cvFile;
    private String cvText;
    private String cvFileName;

    // Step 3: Voice & Language
    @NotBlank(message = "{validation.language.required}")
    private String language = "bg";

    @NotBlank(message = "{validation.voice.required}")
    private String voiceId = "Algieba";

    private String interviewerNameEN = "George";
    private String interviewerNameBG = "Георги";

    /**
     * Gets the effective position (custom or selected).
     */
    public String getEffectivePosition() {
        if ("custom".equals(position) && customPosition != null && !customPosition.isBlank()) {
            return customPosition.trim();
        }
        return position;
    }

    /**
     * Clears CV data.
     */
    public void clearCv() {
        this.cvFile = null;
        this.cvText = null;
        this.cvFileName = null;
    }

}
