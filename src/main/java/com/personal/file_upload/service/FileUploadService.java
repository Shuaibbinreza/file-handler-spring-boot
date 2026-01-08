package com.personal.file_upload.service;

import com.personal.file_upload.persistance.entity.FileEntity;
import com.personal.file_upload.persistance.repository.FileUploadRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class FileUploadService {

    @Value("${file.upload-dir}")
    private String uploadDir;

    private final FileUploadRepository repository;

    public FileUploadService(FileUploadRepository repository) {
        this.repository = repository;
    }

    public FileEntity uploadFile(MultipartFile file) throws IOException {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        // 1️⃣ Ensure upload directory exists
        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(uploadPath);

        // 2️⃣ Clean original filename
        String originalName = StringUtils.cleanPath(Objects.requireNonNull(file.getOriginalFilename()));

        // 3️⃣ Laravel-style timestamp to prevent overwriting
        String storedName = System.currentTimeMillis() + "_" + originalName;

        // 4️⃣ Save file to disk
        Path filePath = uploadPath.resolve(storedName);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        // 5️⃣ Save metadata in DB
        FileEntity uploadedFile = new FileEntity();
        uploadedFile.setOriginalName(originalName);
        uploadedFile.setStoredName(storedName);
        uploadedFile.setFilePath("uploads/" + storedName); // relative path
        uploadedFile.setUploadedAt(LocalDateTime.now());

        return repository.save(uploadedFile);
    }
}