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

@Slf4j
@Service
public class CvProcessingService {

    private static final long MAX_FILE_SIZE = 2 * 1024 * 1024; // 2MB

    private static final String CONTENT_TYPE_PDF = "application/pdf";

    private static final String CONTENT_TYPE_DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";


    public String extractText(MultipartFile file) throws IOException {
        validateFile(file);

        String contentType = file.getContentType();
        log.info("Processing CV file: {} ({}), size: {} bytes", file.getOriginalFilename(), contentType, file.getSize());

        if (CONTENT_TYPE_PDF.equals(contentType)) {
            return extractFromPdf(file);
        } else if (CONTENT_TYPE_DOCX.equals(contentType)) {
            return extractFromDocx(file);
        } else {
            throw new IllegalArgumentException("Unsupported file type: " + contentType);
        }
    }//extractText


    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty or null");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds maximum allowed (2MB)");
        }

        String contentType = file.getContentType();
        if (!CONTENT_TYPE_PDF.equals(contentType) && !CONTENT_TYPE_DOCX.equals(contentType)) {
            throw new IllegalArgumentException("Invalid file type. Only PDF and DOCX are allowed.");
        }
    }//validateFile


    private String extractFromPdf(MultipartFile file) throws IOException {
        try (InputStream inputStream = file.getInputStream();
             PDDocument document = Loader.loadPDF(inputStream.readAllBytes())) {

            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);

            log.info("Extracted {} characters from PDF", text.length());
            return cleanText(text);
        }
    }//extractFromPdf


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
    }//extractFromDocx


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
    }//cleanText

}//CvProcessingService
