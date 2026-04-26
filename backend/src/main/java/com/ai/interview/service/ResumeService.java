package com.ai.interview.service;

import com.ai.interview.entity.AnalysisStatus;
import com.ai.interview.entity.AsyncJob;
import com.ai.interview.entity.JobType;
import com.ai.interview.entity.Resume;
import com.ai.interview.exception.ResourceNotFoundException;
import com.ai.interview.repository.ResumeRepository;
import com.ai.interview.security.LoginUserContextService;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * 简历上传 / 管理服务。
 * <p>
 * 职责边界：
 * <ul>
 *   <li>Java 侧负责：接收文件、校验、上传 MinIO、写 DB、创建 async_job。</li>
 *   <li>Python Worker 负责：从 async_jobs 拉任务、解析 PDF 结构化、写回 parsed_content。</li>
 * </ul>
 */
@Service
@Slf4j
public class ResumeService {

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024; // 10 MB
    private static final String RESUME_ENTITY_TYPE = "resume";

    private final ResumeRepository resumeRepository;
    private final StorageService storageService;
    private final AsyncJobService asyncJobService;
    private final LoginUserContextService loginUserContextService;

    public ResumeService(ResumeRepository resumeRepository,
                         StorageService storageService,
                         AsyncJobService asyncJobService,
                         LoginUserContextService loginUserContextService) {
        this.resumeRepository = resumeRepository;
        this.storageService = storageService;
        this.asyncJobService = asyncJobService;
        this.loginUserContextService = loginUserContextService;
    }

    /**
     * 上传简历 PDF：校验 → 存 MinIO → 写 DB → 创建解析任务。
     * 同时将该用户所有其他简历设为非活跃。
     */
    @Transactional
    public Resume uploadResume(MultipartFile file) {
        Long userId = loginUserContextService.requireUserId();

        validateFile(file);

        String contentHash = computeSha256(file);
        String objectName = "user/%d/%s.pdf".formatted(userId, contentHash);

        storageService.upload(storageService.getResumeBucket(), objectName, file);

        resumeRepository.deactivateAllByUserId(userId);

        Resume resume = new Resume();
        resume.setUserId(userId);
        resume.setOriginalFilename(file.getOriginalFilename());
        resume.setFilePath(objectName);
        resume.setFileSizeBytes(file.getSize());
        resume.setContentHash(contentHash);
        resume.setAnalysisStatus(AnalysisStatus.PENDING);
        resume.setIsActive(true);

        Resume saved = resumeRepository.save(resume);
        log.info("Resume saved: id={}, user={}, file={}", saved.getId(), userId, objectName);

        AsyncJob job = asyncJobService.createJob(
                userId,
                JobType.RESUME_PARSE,
                RESUME_ENTITY_TYPE,
                saved.getId(),
                Map.of(
                        "resume_id", saved.getId(),
                        "file_path", objectName,
                        "bucket", storageService.getResumeBucket()
                )
        );
        log.info("Created RESUME_PARSE job {} for resume {}", job.getId(), saved.getId());

        return saved;
    }

    public List<Resume> listMyResumes() {
        Long userId = loginUserContextService.requireUserId();
        return resumeRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId);
    }

    public Resume getMyResume(Long resumeId) {
        Long userId = loginUserContextService.requireUserId();
        return resumeRepository.findByIdAndUserIdAndDeletedAtIsNull(resumeId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Resume not found"));
    }

    /**
     * 获取简历文件的预签名下载 URL（有效期 1 小时）。
     */
    public String getDownloadUrl(Long resumeId) {
        Resume resume = getMyResume(resumeId);
        return storageService.generatePresignedUrl(storageService.getResumeBucket(), resume.getFilePath());
    }

    /**
     * 软删除简历（如果是活跃简历则同时清除 isActive）。
     */
    @Transactional
    public void deleteResume(Long resumeId) {
        Resume resume = getMyResume(resumeId);
        resume.markDeleted();
        resume.setIsActive(false);
        resumeRepository.save(resume);
        log.info("Resume {} soft-deleted by user {}", resumeId, resume.getUserId());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 私有工具
    // ──────────────────────────────────────────────────────────────────────────

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File must not be empty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds 10 MB limit");
        }
        String contentType = file.getContentType();
        if (!"application/pdf".equals(contentType)) {
            throw new IllegalArgumentException("Only PDF files are accepted");
        }
        validatePdfMagicBytes(file);
    }

    private void validatePdfMagicBytes(MultipartFile file) {
        try {
            // PDFBox 解析文件头，防止伪造 content-type
            try (PDDocument ignored = Loader.loadPDF(file.getBytes())) {
                // 能正常 load 说明是合法 PDF
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid or corrupted PDF file");
        }
    }

    private String computeSha256(MultipartFile file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(file.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute file hash", e);
        }
    }
}
