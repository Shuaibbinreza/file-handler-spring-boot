package com.personal.file_upload.controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.personal.file_upload.persistance.entity.FileEntity;
import com.personal.file_upload.service.FileUploadService;

@Controller
public class WebController {
    public final FileUploadService fileUpload;

    public WebController(FileUploadService fileService){
        this.fileUpload = fileService;
    }

    @GetMapping("/")
    public String showFiles(Model model){
        List<FileEntity> files = fileUpload.getAllFiles();
        model.addAttribute("files", files);
        return "all_file";
    }
}
