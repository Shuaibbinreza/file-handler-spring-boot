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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
@CrossOrigin(origins = "*")
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

    // Initialize chunked upload
    @PostMapping("/chunked/init")
    public ResponseEntity<Map<String, String>> initChunkedUpload(
            @RequestParam("filename") String filename,
            @RequestParam("totalSize") long totalSize,
            @RequestParam(value = "contentType", required = false) String contentType
    ) {
        try {
            String uploadId = fileService.initChunkedUpload(filename, totalSize, contentType != null ? contentType : "application/octet-stream");
            
            Map<String, String> response = new HashMap<>();
            response.put("uploadId", uploadId);
            response.put("status", "initialized");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // Upload individual chunk
    @PostMapping("/chunked/upload")
    public ResponseEntity<Map<String, Object>> uploadChunk(
            @RequestParam("uploadId") String uploadId,
            @RequestParam("chunkIndex") int chunkIndex,
            @RequestParam("chunk") MultipartFile chunk
    ) {
        try {
            fileService.uploadChunk(uploadId, chunkIndex, chunk.getBytes());
            
            double progress = fileService.getUploadProgress(uploadId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "chunk_received");
            response.put("chunkIndex", chunkIndex);
            response.put("progress", progress);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            // Check if it's because chunk already exists
            if (e.getMessage() != null && e.getMessage().contains("already exists")) {
                double progress = fileService.getUploadProgress(uploadId);
                Map<String, Object> response = new HashMap<>();
                response.put("status", "chunk_exists");
                response.put("chunkIndex", chunkIndex);
                response.put("progress", progress);
                return ResponseEntity.ok(response);
            }
            Map<String, Object> error = new HashMap<>();
            error.put("status", "error");
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    // Complete chunked upload
    @PostMapping("/chunked/complete")
    public ResponseEntity<?> completeChunkedUpload(@RequestParam("uploadId") String uploadId) {
        try {
            FileEntity uploaded = fileService.completeChunkedUpload(uploadId);
            return ResponseEntity.ok(uploaded);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("status", "error");
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    // Get upload progress
    @GetMapping("/chunked/progress/{uploadId}")
    public ResponseEntity<Map<String, Object>> getUploadProgress(@PathVariable String uploadId) {
        try {
            double progress = fileService.getUploadProgress(uploadId);
            String status = fileService.getUploadStatus(uploadId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("uploadId", uploadId);
            response.put("progress", progress);
            response.put("status", status);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // Pause an ongoing upload
    @PostMapping("/chunked/pause/{uploadId}")
    public ResponseEntity<Map<String, Object>> pauseUpload(@PathVariable String uploadId) {
        try {
            boolean success = fileService.pauseUpload(uploadId);
            
            Map<String, Object> response = new HashMap<>();
            if (success) {
                response.put("status", "success");
                response.put("message", "Upload paused");
            } else {
                response.put("status", "error");
                response.put("message", "Upload not found or already completed");
            }
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("status", "error");
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    // Resume a paused upload
    @PostMapping("/chunked/resume/{uploadId}")
    public ResponseEntity<Map<String, Object>> resumeUpload(@PathVariable String uploadId) {
        try {
            boolean success = fileService.resumeUpload(uploadId);
            
            Map<String, Object> response = new HashMap<>();
            if (success) {
                response.put("status", "success");
                response.put("message", "Upload resumed");
            } else {
                response.put("status", "error");
                response.put("message", "Upload not found or not paused");
            }
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("status", "error");
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    // Cancel an upload
    @PostMapping("/chunked/cancel/{uploadId}")
    public ResponseEntity<Map<String, Object>> cancelUpload(@PathVariable String uploadId) {
        try {
            boolean success = fileService.cancelUpload(uploadId);
            
            Map<String, Object> response = new HashMap<>();
            if (success) {
                response.put("status", "success");
                response.put("message", "Upload cancelled");
            } else {
                response.put("status", "error");
                response.put("message", "Upload not found");
            }
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("status", "error");
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
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

            // Determine content type based on file extension
            String contentType = file.getContentType();
            if (contentType == null || contentType.isEmpty()) {
                contentType = "application/octet-stream";
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + file.getOriginalName() + "\"")
                    .header("Content-Length", String.valueOf(file.getFileSize()))
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