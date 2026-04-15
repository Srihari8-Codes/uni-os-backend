package com.unios.service.documents;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
public class PdfExtractionService {

    public String extractText(MultipartFile file) {
        if (file.isEmpty()) {
            return "";
        }

        try (PDDocument document = PDDocument.load(file.getInputStream())) {
            if (document.isEncrypted()) {
                return "ERROR: Encrypted PDF";
            }
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        } catch (IOException e) {
            e.printStackTrace();
            return "ERROR extracting text: " + e.getMessage();
        }
    }
}
