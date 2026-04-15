package com.unios.service.documents;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;

@Service
public class FileStorageService {

    private final Path fileStorageLocation;

    public FileStorageService() {
        this.fileStorageLocation = Paths.get("uploads/resumes").toAbsolutePath().normalize();
        
        try {
            Files.createDirectories(this.fileStorageLocation);
        } catch (Exception ex) {
            throw new RuntimeException("Could not create the directory where the uploaded files will be stored.", ex);
        }
    }

    public String storeFile(MultipartFile file, Long candidateId) {
        String originalFileName = file.getOriginalFilename();
        if (originalFileName == null) originalFileName = "resume.pdf";
        
        String fileExtension = "";
        int i = originalFileName.lastIndexOf('.');
        if (i > 0) fileExtension = originalFileName.substring(i);

        String targetFileName = "candidate_" + candidateId + "_" + Instant.now().toEpochMilli() + fileExtension;

        try {
            if (targetFileName.contains("..")) {
                throw new RuntimeException("Filename contains invalid path sequence " + targetFileName);
            }

            Path targetLocation = this.fileStorageLocation.resolve(targetFileName);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            return targetLocation.toString();
        } catch (IOException ex) {
            throw new RuntimeException("Could not store file " + targetFileName + ". Please try again!", ex);
        }
    }
}
