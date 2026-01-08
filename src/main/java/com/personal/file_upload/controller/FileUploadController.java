package com.personal.file_upload.controller;

import com.personal.file_upload.persistance.entity.FileEntity;
import com.personal.file_upload.persistance.repository.FileUploadRepository;
import com.personal.file_upload.service.FileUploadService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Objects;

@RestController
@RequestMapping("/api/files")
public class FileUploadController {

    @Value("${file.upload-dir}")
    private String uploadDir;

    // private final FileUploadRepository repository;
    private final FileUploadService fileService;

    public FileUploadController(FileUploadService fileService) {
        // this.repository = repository;
        this.fileService = fileService;
    }

    // @PostMapping("/upload")
    // public ResponseEntity<String> uploadFile(@RequestParam("file") MultipartFile file) {

    //     if (file == null || file.isEmpty()) {
    //         return ResponseEntity.badRequest().body("File is empty");
    //     }

    //     try {
    //         // ✅ Resolve upload directory and ensure it exists
    //         Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
    //         Files.createDirectories(uploadPath);

    //         // ✅ Clean original filename
    //         String originalName = StringUtils.cleanPath(Objects.requireNonNull(file.getOriginalFilename()));

    //         // ✅ Laravel-style: add timestamp to prevent overwriting
    //         String storedName = System.currentTimeMillis() + "_" + originalName;

    //         // ✅ Save file to disk using streaming (memory-safe)
    //         Path filePath = uploadPath.resolve(storedName);
    //         Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

    //         // ✅ Save metadata in DB
    //         FileEntity uploadedFile = new FileEntity();
    //         uploadedFile.setOriginalName(originalName);
    //         uploadedFile.setStoredName(storedName);

    //         // 🔹 Save **relative path**, not absolute
    //         uploadedFile.setFilePath("uploads/" + storedName); // Only relative path
    //         uploadedFile.setUploadedAt(LocalDateTime.now());
    //         repository.save(uploadedFile);

    //         return ResponseEntity.ok("File uploaded & saved in DB: " + storedName);

    //     } catch (IOException e) {
    //         e.printStackTrace();
    //         return ResponseEntity.internalServerError().body("Upload failed: " + e.getMessage());
    //     }
    // }

    @PostMapping("/upload")
    public String uploadFile(@RequestParam("file") MultipartFile file, Model model) {

        try {
            FileEntity uploaded = fileService.uploadFile(file);
            model.addAttribute("msg", "File uploaded successfully: " + uploaded.getStoredName());
        } catch (Exception e) {
            model.addAttribute("msg", "Upload failed: " + e.getMessage());
        }

        return "index";
    }
}