package com.personal.file_upload.persistance.repository;

import com.personal.file_upload.persistance.entity.FileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FileUploadRepository extends JpaRepository<FileEntity, Long> {
}
