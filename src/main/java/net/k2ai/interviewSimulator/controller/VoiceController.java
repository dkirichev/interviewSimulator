package net.k2ai.interviewSimulator.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("/api/voices")
public class VoiceController {

	private static final Set<String> VALID_VOICE_IDS = Set.of("Algieba", "Kore", "Fenrir", "Despina");

	private static final Set<String> VALID_LANGUAGES = Set.of("EN", "BG");


	@GetMapping
	public ResponseEntity<List<Map<String, Object>>> getAvailableVoices() {
		// Return voices in the specified order: Algieba, Kore, Fenrir, Despina
		List<Map<String, Object>> voices = List.of(
				Map.of(
						"id", "Algieba",
						"nameEN", "George",
						"nameBG", "Георги",
						"gender", "male"
				),
				Map.of(
						"id", "Kore",
						"nameEN", "Victoria",
						"nameBG", "Виктория",
						"gender", "female"
				),
				Map.of(
						"id", "Fenrir",
						"nameEN", "Max",
						"nameBG", "Макс",
						"gender", "male"
				),
				Map.of(
						"id", "Despina",
						"nameEN", "Diana",
						"nameBG", "Диана",
						"gender", "female"
				)
		);

		return ResponseEntity.ok(voices);
	}//getAvailableVoices


	@GetMapping("/preview/{voiceId}/{language}")
	public ResponseEntity<Resource> getVoicePreview(
			@PathVariable String voiceId,
			@PathVariable String language) {

		// Validate voice ID
		if (!VALID_VOICE_IDS.contains(voiceId)) {
			log.warn("Invalid voice ID requested: {}", voiceId);
			return ResponseEntity.badRequest().build();
		}

		// Validate language
		String lang = language.toUpperCase();
		if (!VALID_LANGUAGES.contains(lang)) {
			log.warn("Invalid language requested: {}", language);
			return ResponseEntity.badRequest().build();
		}

		try {
			String filename = String.format("static/audio/voices/%s_%s.wav", voiceId, lang);
			Resource resource = new ClassPathResource(filename);

			if (!resource.exists()) {
				log.error("Voice file not found: {}", filename);
				return ResponseEntity.notFound().build();
			}

			log.debug("Serving voice preview: {}", filename);

			return ResponseEntity.ok()
					.contentType(MediaType.parseMediaType("audio/wav"))
					.header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
					.body(resource);

		} catch (Exception e) {
			log.error("Error serving voice preview for {} / {}", voiceId, language, e);
			return ResponseEntity.internalServerError().build();
		}
	}//getVoicePreview

}//VoiceController
