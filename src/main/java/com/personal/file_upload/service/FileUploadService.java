package com.personal.file_upload.service;

import com.personal.file_upload.persistance.entity.FileEntity;
import com.personal.file_upload.persistance.repository.FileUploadRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FileUploadService {

    @Value("${file.upload-dir}")
    private String uploadDir;

    // Store upload progress for tracking
    private final Map<String, UploadProgress> uploadProgressMap = new ConcurrentHashMap<>();

    private final FileUploadRepository repository;

    public FileUploadService(FileUploadRepository repository) {
        this.repository = repository;
    }

    public List<FileEntity> getAllFiles() {
        return repository.findAll();
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
        uploadedFile.setFileSize(file.getSize());
        uploadedFile.setContentType(file.getContentType());
        uploadedFile.setUploadedAt(LocalDateTime.now());

        return repository.save(uploadedFile);
    }

    // Chunked upload for large files
    public String initChunkedUpload(String filename, long totalSize, String contentType) {
        String uploadId = UUID.randomUUID().toString();
        String storedName = System.currentTimeMillis() + "_" + filename;
        
        UploadProgress progress = new UploadProgress();
        progress.setUploadId(uploadId);
        progress.setFilename(filename);
        progress.setStoredName(storedName);
        progress.setTotalSize(totalSize);
        progress.setContentType(contentType);
        progress.setStatus("in_progress");
        
        uploadProgressMap.put(uploadId, progress);
        
        return uploadId;
    }

    public void uploadChunk(String uploadId, int chunkIndex, byte[] chunkData) throws IOException {
        UploadProgress progress = uploadProgressMap.get(uploadId);
        if (progress == null) {
            throw new IllegalArgumentException("Invalid upload ID");
        }

        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(uploadPath);

        // Check if chunk already exists (for resume scenario)
        Path chunkFile = uploadPath.resolve(progress.getStoredName() + ".chunk" + chunkIndex);
        if (Files.exists(chunkFile)) {
            // Chunk already exists, just update progress tracking if not already tracked
            if (!progress.getChunks().containsKey(chunkIndex)) {
                progress.addChunk(chunkIndex, chunkData.length);
                progress.setUploadedBytes(progress.getUploadedBytes() + chunkData.length);
            }
            return;
        }

        // Write chunk to temporary file
        Files.write(chunkFile, chunkData);

        progress.addChunk(chunkIndex, chunkData.length);
        progress.setUploadedBytes(progress.getUploadedBytes() + chunkData.length);
    }

    public FileEntity completeChunkedUpload(String uploadId) throws IOException {
        UploadProgress progress = uploadProgressMap.get(uploadId);
        if (progress == null) {
            throw new IllegalArgumentException("Invalid upload ID");
        }

        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path finalFilePath = uploadPath.resolve(progress.getStoredName());

        // Merge all chunks
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(finalFilePath))) {
            int totalChunks = progress.getChunks().size();
            for (int i = 0; i < totalChunks; i++) {
                Path chunkFile = uploadPath.resolve(progress.getStoredName() + ".chunk" + i);
                if (Files.exists(chunkFile)) {
                    byte[] chunkData = Files.readAllBytes(chunkFile);
                    out.write(chunkData);
                    Files.delete(chunkFile); // Clean up chunk file
                }
            }
        }

        // Save metadata in DB
        FileEntity uploadedFile = new FileEntity();
        uploadedFile.setOriginalName(progress.getFilename());
        uploadedFile.setStoredName(progress.getStoredName());
        uploadedFile.setFilePath("uploads/" + progress.getStoredName());
        uploadedFile.setFileSize(progress.getTotalSize());
        uploadedFile.setContentType(progress.getContentType());
        uploadedFile.setUploadedAt(LocalDateTime.now());

        // Clean up progress
        uploadProgressMap.remove(uploadId);

        return repository.save(uploadedFile);
    }

    public double getUploadProgress(String uploadId) {
        UploadProgress progress = uploadProgressMap.get(uploadId);
        if (progress == null || progress.getTotalSize() == 0) {
            return 0;
        }
        return (double) progress.getUploadedBytes() / progress.getTotalSize() * 100;
    }

    // Pause an ongoing upload
    public boolean pauseUpload(String uploadId) {
        UploadProgress progress = uploadProgressMap.get(uploadId);
        if (progress == null) {
            return false;
        }
        progress.setStatus("paused");
        return true;
    }

    // Resume a paused upload
    public boolean resumeUpload(String uploadId) {
        UploadProgress progress = uploadProgressMap.get(uploadId);
        if (progress == null) {
            return false;
        }
        if (!"paused".equals(progress.getStatus())) {
            return false;
        }
        
        // Recalculate uploaded bytes based on existing chunks
        try {
            Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
            long uploadedBytes = 0;
            int chunkIndex = 0;
            while (true) {
                Path chunkFile = uploadPath.resolve(progress.getStoredName() + ".chunk" + chunkIndex);
                if (Files.exists(chunkFile)) {
                    uploadedBytes += Files.size(chunkFile);
                    progress.addChunk(chunkIndex, (int) Files.size(chunkFile));
                    chunkIndex++;
                } else {
                    break;
                }
            }
            progress.setUploadedBytes(uploadedBytes);
        } catch (IOException e) {
            // Ignore errors, use existing progress
        }
        
        progress.setStatus("in_progress");
        return true;
    }

    // Get upload status
    public String getUploadStatus(String uploadId) {
        UploadProgress progress = uploadProgressMap.get(uploadId);
        if (progress == null) {
            return "not_found";
        }
        return progress.getStatus();
    }

    // Cancel an upload
    public boolean cancelUpload(String uploadId) {
        UploadProgress progress = uploadProgressMap.get(uploadId);
        if (progress == null) {
            return false;
        }

        try {
            // Delete all chunk files
            Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
            int totalChunks = progress.getChunks().size();
            for (int i = 0; i < totalChunks; i++) {
                Path chunkFile = uploadPath.resolve(progress.getStoredName() + ".chunk" + i);
                Files.deleteIfExists(chunkFile);
            }
        } catch (IOException e) {
            // Ignore cleanup errors
        }

        uploadProgressMap.remove(uploadId);
        return true;
    }

    public FileEntity getFileById(Long id) {
        return repository.findById(id).orElseThrow(() -> new RuntimeException("File not found with id: " + id));
    }

    public void deleteFileById(Long id) {
        FileEntity file = getFileById(id); // throws exception if not found

        try {
            // Delete from disk
            Path filePath = Paths.get(uploadDir)
                    .resolve(file.getStoredName())
                    .normalize();
            Files.deleteIfExists(filePath);

            // Delete from DB
            repository.deleteById(id);
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete file: " + e.getMessage(), e);
        }
    }

    // Add getter for uploadDir (used in controller)
    public String getUploadDir() {
        return uploadDir;
    }

    // Inner class to track upload progress
    public static class UploadProgress {
        private String uploadId;
        private String filename;
        private String storedName;
        private long totalSize;
        private long uploadedBytes;
        private String contentType;
        private String status;
        private Map<Integer, Integer> chunks = new HashMap<>();

        public String getUploadId() { return uploadId; }
        public void setUploadId(String uploadId) { this.uploadId = uploadId; }
        public String getFilename() { return filename; }
        public void setFilename(String filename) { this.filename = filename; }
        public String getStoredName() { return storedName; }
        public void setStoredName(String storedName) { this.storedName = storedName; }
        public long getTotalSize() { return totalSize; }
        public void setTotalSize(long totalSize) { this.totalSize = totalSize; }
        public long getUploadedBytes() { return uploadedBytes; }
        public void setUploadedBytes(long uploadedBytes) { this.uploadedBytes = uploadedBytes; }
        public String getContentType() { return contentType; }
        public void setContentType(String contentType) { this.contentType = contentType; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Map<Integer, Integer> getChunks() { return chunks; }
        public void addChunk(int index, int size) { this.chunks.put(index, size); }
    }
}