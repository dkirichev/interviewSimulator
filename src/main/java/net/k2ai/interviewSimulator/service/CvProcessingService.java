package net.k2ai.interviewSimulator.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

@Slf4j
@Service
public class CvProcessingService {

	private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
	private static final int MAX_EXTRACTED_LENGTH = 100_000; // 100KB max text

	private static final String CONTENT_TYPE_PDF = "application/pdf";
	private static final String CONTENT_TYPE_DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

	// Magic bytes for file type verification
	private static final byte[] PDF_MAGIC = {0x25, 0x50, 0x44, 0x46}; // %PDF
	private static final byte[] DOCX_MAGIC = {0x50, 0x4B, 0x03, 0x04}; // PK (ZIP format)


	public String extractText(MultipartFile file) throws IOException {
		validateFile(file);

		String contentType = file.getContentType();
		log.info("Processing CV file: {} ({}), size: {} bytes",
				sanitizeFilename(file.getOriginalFilename()), contentType, file.getSize());

		String extractedText;
		if (CONTENT_TYPE_PDF.equals(contentType)) {
			extractedText = extractFromPdf(file);
		} else if (CONTENT_TYPE_DOCX.equals(contentType)) {
			extractedText = extractFromDocx(file);
		} else {
			throw new IllegalArgumentException("Unsupported file type: " + contentType);
		}

		// Limit extracted text length
		if (extractedText.length() > MAX_EXTRACTED_LENGTH) {
			log.warn("CV text truncated from {} to {} characters", extractedText.length(), MAX_EXTRACTED_LENGTH);
			extractedText = extractedText.substring(0, MAX_EXTRACTED_LENGTH);
		}

		return extractedText;
	}// extractText


	private void validateFile(MultipartFile file) throws IOException {
		if (file == null || file.isEmpty()) {
			throw new IllegalArgumentException("File is empty or null");
		}

		if (file.getSize() > MAX_FILE_SIZE) {
			throw new IllegalArgumentException("File size exceeds maximum allowed (10MB)");
		}

		String contentType = file.getContentType();
		if (!CONTENT_TYPE_PDF.equals(contentType) && !CONTENT_TYPE_DOCX.equals(contentType)) {
			throw new IllegalArgumentException("Invalid file type. Only PDF and DOCX are allowed.");
		}

		// Verify magic bytes to prevent content-type spoofing
		verifyMagicBytes(file, contentType);
	}// validateFile


	private void verifyMagicBytes(MultipartFile file, String contentType) throws IOException {
		byte[] fileHeader = new byte[4];
		try (InputStream is = file.getInputStream()) {
			int bytesRead = is.read(fileHeader);
			if (bytesRead < 4) {
				throw new IllegalArgumentException("File is too small or corrupted");
			}
		}

		if (CONTENT_TYPE_PDF.equals(contentType)) {
			if (!Arrays.equals(Arrays.copyOf(fileHeader, 4), PDF_MAGIC)) {
				log.warn("File claims to be PDF but magic bytes don't match: {}",
						bytesToHex(fileHeader));
				throw new IllegalArgumentException("Invalid PDF file - file content doesn't match");
			}
		} else if (CONTENT_TYPE_DOCX.equals(contentType)) {
			if (!Arrays.equals(Arrays.copyOf(fileHeader, 4), DOCX_MAGIC)) {
				log.warn("File claims to be DOCX but magic bytes don't match: {}",
						bytesToHex(fileHeader));
				throw new IllegalArgumentException("Invalid DOCX file - file content doesn't match");
			}
		}
	}// verifyMagicBytes


	private String extractFromPdf(MultipartFile file) throws IOException {
		try (InputStream inputStream = file.getInputStream();
			 PDDocument document = Loader.loadPDF(inputStream.readAllBytes())) {

			PDFTextStripper stripper = new PDFTextStripper();
			String text = stripper.getText(document);

			log.info("Extracted {} characters from PDF", text.length());
			return cleanText(text);
		}
	}// extractFromPdf


	private String extractFromDocx(MultipartFile file) throws IOException {
		try (InputStream inputStream = file.getInputStream();
			 XWPFDocument document = new XWPFDocument(inputStream)) {

			StringBuilder text = new StringBuilder();

			for (XWPFParagraph paragraph : document.getParagraphs()) {
				text.append(paragraph.getText()).append("\n");
			}

			String result = text.toString();
			log.info("Extracted {} characters from DOCX", result.length());
			return cleanText(result);
		}
	}// extractFromDocx


	private String cleanText(String text) {
		if (text == null) {
			return "";
		}

		// Remove excessive whitespace while preserving paragraph structure
		return text
				.replaceAll("\\r\\n", "\n")
				.replaceAll("\\r", "\n")
				.replaceAll("[ \\t]+", " ")
				.replaceAll("\\n{3,}", "\n\n")
				.trim();
	}// cleanText


	private String sanitizeFilename(String filename) {
		if (filename == null) {
			return "unknown";
		}
		// Remove path traversal attempts and dangerous characters
		return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
	}// sanitizeFilename


	private String bytesToHex(byte[] bytes) {
		StringBuilder sb = new StringBuilder();
		for (byte b : bytes) {
			sb.append(String.format("%02X ", b));
		}
		return sb.toString().trim();
	}// bytesToHex

}// CvProcessingService
