package com.ai.interview.controller;

import com.ai.interview.entity.Resume;
import com.ai.interview.service.ResumeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 简历管理接口。
 *
 * <pre>
 * POST   /resume/upload          上传 PDF（multipart/form-data，字段名 file）
 * GET    /resume                 当前用户的简历列表
 * GET    /resume/{id}            单条简历元数据
 * GET    /resume/{id}/url        预签名下载 URL（有效期 1 小时）
 * DELETE /resume/{id}            软删除
 * </pre>
 */
@RestController
@RequestMapping("/resume")
@Slf4j
public class ResumeController {

    private final ResumeService resumeService;

    public ResumeController(ResumeService resumeService) {
        this.resumeService = resumeService;
    }

    @PostMapping("/upload")
    public ResponseEntity<Resume> upload(@RequestParam("file") MultipartFile file) {
        Resume resume = resumeService.uploadResume(file);
        return ResponseEntity.ok(resume);
    }

    @GetMapping
    public ResponseEntity<List<Resume>> list() {
        return ResponseEntity.ok(resumeService.listMyResumes());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Resume> get(@PathVariable Long id) {
        return ResponseEntity.ok(resumeService.getMyResume(id));
    }

    @GetMapping("/{id}/url")
    public ResponseEntity<Map<String, String>> downloadUrl(@PathVariable Long id) {
        String url = resumeService.getDownloadUrl(id);
        return ResponseEntity.ok(Map.of("url", url));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        resumeService.deleteResume(id);
        return ResponseEntity.noContent().build();
    }
}
