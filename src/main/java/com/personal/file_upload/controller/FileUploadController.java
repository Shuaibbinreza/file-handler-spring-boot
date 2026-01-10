package com.personal.file_upload.controller;

import com.personal.file_upload.persistance.entity.FileEntity;
import com.personal.file_upload.service.FileUploadService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

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

    @GetMapping
    public ResponseEntity<List<FileEntity>> getAllFiles() {
        try {
            List<FileEntity> files = fileService.getAllFiles();
            return ResponseEntity.ok(files);
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    @PostMapping("/upload")
    public ResponseEntity<FileEntity> uploadFile(
            @RequestParam("file") MultipartFile file
    ) {
        try {
            FileEntity uploaded = fileService.uploadFile(file);
            return ResponseEntity.ok(uploaded);
        } catch (Exception e) {
            return ResponseEntity
                    .badRequest()
                    .build();
        }
    }


    @GetMapping("/download/{id}")
    public ResponseEntity<Resource> downloadFile(@PathVariable Long id) {

        try {
            FileEntity file = fileService.getFileById(id);

            Path filePath = Paths.get(uploadDir)
                    .resolve(file.getStoredName())
                    .normalize();

            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists()) {
                throw new RuntimeException("File not found");
            }

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + file.getOriginalName() + "\"")
                    .body(resource);

        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteFile(@PathVariable Long id) {
        try {
            fileService.deleteFileById(id);
            return ResponseEntity.ok("File deleted successfully");
        } catch (Exception e) {
            return ResponseEntity.status(400).body("Delete failed: " + e.getMessage());
        }
    }
}