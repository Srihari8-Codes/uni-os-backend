package com.unios.service.documents;

import com.unios.model.Application;
import com.unios.model.ExamSchedule;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;

@Service
public class PdfGenerationService {

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    public byte[] generateApplicationPdf(Application app) {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                
                // Helper to safely show text (strips non-WinAnsi chars)
                java.util.function.Consumer<String> safeShow = (txt) -> {
                    try {
                        if (txt == null) return;
                        // Clean string for standard PDF fonts (WinAnsiEncoding)
                        String cleaned = txt.replaceAll("[^\\x00-\\x7F]", "");
                        contentStream.showText(cleaned);
                    } catch (Exception e) {}
                };

                // University Header
                contentStream.beginText();
                contentStream.setFont(PDType1Font.HELVETICA_BOLD, 22);
                contentStream.newLineAtOffset(150, 720);
                contentStream.showText("UNIVERSITY OS - ADMISSIONS");
                contentStream.endText();

                // Application Summary Title
                contentStream.beginText();
                contentStream.setFont(PDType1Font.HELVETICA_BOLD, 18);
                contentStream.newLineAtOffset(180, 680);
                contentStream.showText("Official Application Summary");
                contentStream.endText();

                // Separator Line
                contentStream.moveTo(50, 660);
                contentStream.lineTo(550, 660);
                contentStream.stroke();

                // Section: Personal Details
                contentStream.beginText();
                contentStream.setFont(PDType1Font.HELVETICA_BOLD, 14);
                contentStream.newLineAtOffset(50, 620);
                contentStream.showText("1. Personal Information");
                
                contentStream.setFont(PDType1Font.HELVETICA, 12);
                contentStream.newLineAtOffset(0, -25);
                contentStream.showText("Full Name: ");
                safeShow.accept(app.getFullName());
                contentStream.newLineAtOffset(0, -20);
                contentStream.showText("Email Address: ");
                safeShow.accept(app.getEmail());
                contentStream.newLineAtOffset(0, -20);
                contentStream.showText("Application ID: APP-2024-" + String.format("%04d", app.getId()));
                contentStream.endText();

                // Section: Academic Details
                contentStream.beginText();
                contentStream.setFont(PDType1Font.HELVETICA_BOLD, 14);
                contentStream.newLineAtOffset(50, 480);
                contentStream.showText("2. Academic Background");
                
                contentStream.setFont(PDType1Font.HELVETICA, 12);
                contentStream.newLineAtOffset(0, -25);
                String batchName = (app.getBatch() != null) ? app.getBatch().getName() : "Not Specified";
                contentStream.showText("Batch / Program: ");
                safeShow.accept(batchName);
                contentStream.newLineAtOffset(0, -20);
                contentStream.showText("Academic Score / Percentile: " + app.getAcademicScore() + "%");
                contentStream.endText();

                // Section: Declaration & Status
                contentStream.beginText();
                contentStream.setFont(PDType1Font.HELVETICA_BOLD, 14);
                contentStream.newLineAtOffset(50, 360);
                contentStream.showText("3. Submission Declaration");
                
                contentStream.setFont(PDType1Font.HELVETICA, 11);
                contentStream.newLineAtOffset(0, -25);
                contentStream.showText("This document confirms that your digital application has been received and is currently");
                contentStream.newLineAtOffset(0, -15);
                contentStream.showText("under verification. You will be notified via email for the next steps.");
                contentStream.endText();

                // Verification Footer
                contentStream.beginText();
                contentStream.setFont(PDType1Font.COURIER, 9);
                contentStream.newLineAtOffset(120, 100);
                String uniqueHash = java.util.UUID.randomUUID().toString().toUpperCase();
                contentStream.showText("DIGITAL-VERIFICATION-TOKEN: " + uniqueHash);
                contentStream.endText();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.save(baos);
            return baos.toByteArray();
            
        } catch (Exception e) {
            System.err.println("Failed to generate Application PDF: " + e.getMessage());
            return new byte[0];
        }
    }

    public byte[] generateHallTicket(Application app, ExamSchedule schedule, String roomId, String timeSlot) {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                
                // University Header
                contentStream.beginText();
                contentStream.setFont(PDType1Font.HELVETICA_BOLD, 22);
                contentStream.newLineAtOffset(150, 720);
                contentStream.showText("UNIVERSITY OS - ADMISSIONS");
                contentStream.endText();

                // Hall Ticket Title
                contentStream.beginText();
                contentStream.setFont(PDType1Font.HELVETICA_BOLD, 18);
                contentStream.newLineAtOffset(220, 680);
                contentStream.showText("Entrance Exam Hall Ticket");
                contentStream.endText();

                // Separator Line
                contentStream.moveTo(50, 660);
                contentStream.lineTo(550, 660);
                contentStream.stroke();

                // Student Details Header
                contentStream.beginText();
                contentStream.setFont(PDType1Font.HELVETICA_BOLD, 14);
                contentStream.newLineAtOffset(50, 620);
                contentStream.showText("Candidate Details");
                
                contentStream.setFont(PDType1Font.HELVETICA, 12);
                contentStream.newLineAtOffset(0, -20);
                contentStream.showText("Name: " + app.getFullName());
                contentStream.newLineAtOffset(0, -20);
                contentStream.showText("Application Ref: APP-" + app.getId() + "-" + app.getBatch().getId());
                contentStream.newLineAtOffset(0, -20);
                contentStream.showText("Email: " + app.getEmail());
                contentStream.endText();

                // Exam Details Header
                contentStream.beginText();
                contentStream.setFont(PDType1Font.HELVETICA_BOLD, 14);
                contentStream.newLineAtOffset(300, 620);
                contentStream.showText("Exam Details");
                
                contentStream.setFont(PDType1Font.HELVETICA, 12);
                contentStream.newLineAtOffset(0, -20);
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM yyyy");
                contentStream.showText("Date: " + schedule.getExamDate().format(formatter));
                contentStream.newLineAtOffset(0, -20);
                contentStream.showText("Time: " + timeSlot);
                contentStream.newLineAtOffset(0, -20);
                contentStream.showText("Room Allocation: " + roomId);
                contentStream.endText();

                // Exam Portal Credentials Section (NEW)
                contentStream.beginText();
                contentStream.setFont(PDType1Font.HELVETICA_BOLD, 14);
                contentStream.newLineAtOffset(50, 560);
                contentStream.showText("Entrance Exam Portal Credentials");
                
                contentStream.setFont(PDType1Font.HELVETICA_BOLD, 12);
                contentStream.newLineAtOffset(0, -20);
                contentStream.showText("Portal URL: ");
                contentStream.setFont(PDType1Font.HELVETICA, 12);
                contentStream.showText(frontendUrl + "/exams/entrance-exam");
                
                contentStream.setFont(PDType1Font.HELVETICA_BOLD, 12);
                contentStream.newLineAtOffset(0, -20);
                contentStream.showText("Application ID: ");
                contentStream.setFont(PDType1Font.COURIER_BOLD, 13);
                contentStream.showText("APP-" + app.getId());
                
                contentStream.setFont(PDType1Font.HELVETICA_BOLD, 12);
                contentStream.newLineAtOffset(0, -20);
                contentStream.showText("Exam Password: ");
                contentStream.setFont(PDType1Font.COURIER_BOLD, 13);
                contentStream.showText(app.getExamPassword() != null ? app.getExamPassword() : "RANDOM-PX");
                contentStream.endText();

                // Horizontal Line
                contentStream.moveTo(50, 520);
                contentStream.lineTo(550, 520);
                contentStream.stroke();

                // Instructions
                contentStream.beginText();
                contentStream.setFont(PDType1Font.HELVETICA_BOLD, 14);
                contentStream.newLineAtOffset(50, 480);
                contentStream.showText("Instructions to Candidate:");
                
                contentStream.setFont(PDType1Font.HELVETICA, 11);
                contentStream.newLineAtOffset(0, -20);
                contentStream.showText("1. Print this hall ticket and bring it to the examination center.");
                contentStream.newLineAtOffset(0, -20);
                contentStream.showText("2. Bring a valid original Government ID (Aadhaar, Passport, etc.).");
                contentStream.newLineAtOffset(0, -20);
                contentStream.showText("3. Arrive at least 30 minutes before the scheduled time slot.");
                contentStream.newLineAtOffset(0, -20);
                contentStream.showText("4. Electronic devices and calculators are strictly prohibited.");
                contentStream.endText();

                // Simulated Barcode / Unique Secure Hash
                contentStream.beginText();
                contentStream.setFont(PDType1Font.COURIER_BOLD, 10);
                contentStream.newLineAtOffset(100, 350);
                String uniqueHash = java.util.UUID.randomUUID().toString().toUpperCase();
                contentStream.showText("SECURE-TICKET-REF: |||  " + uniqueHash + "  |||");
                contentStream.endText();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.save(baos);
            return baos.toByteArray();
            
        } catch (Exception e) {
            System.err.println("Failed to generate PDF Hall Ticket: " + e.getMessage());
            e.printStackTrace();
            return new byte[0]; // Fallback empty
        }
    }
}
