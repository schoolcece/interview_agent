package com.ai.interview.service;

import com.ai.interview.entity.AsyncJob;
import com.ai.interview.entity.JobStatus;
import com.ai.interview.entity.JobType;
import com.ai.interview.repository.AsyncJobRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class AsyncJobService {

    private final AsyncJobRepository asyncJobRepository;

    public AsyncJobService(AsyncJobRepository asyncJobRepository) {
        this.asyncJobRepository = asyncJobRepository;
    }

    /**
     * 创建一条异步任务记录，供 Python Worker 拉取执行。
     *
     * @param userId            触发任务的用户
     * @param jobType           任务类型（如 RESUME_PARSE）
     * @param relatedEntityType 关联实体类型字符串（如 "resume"）
     * @param relatedEntityId   关联实体 ID
     * @param payload           额外参数（传给 Worker 的元数据）
     */
    public AsyncJob createJob(Long userId,
                              JobType jobType,
                              String relatedEntityType,
                              Long relatedEntityId,
                              Map<String, Object> payload) {
        AsyncJob job = new AsyncJob();
        job.setUserId(userId);
        job.setJobType(jobType);
        job.setRelatedEntityType(relatedEntityType);
        job.setRelatedEntityId(relatedEntityId);
        job.setStatus(JobStatus.PENDING);
        job.setProgress(0);
        job.setAttemptCount(0);
        job.setMaxAttempts(3);
        job.setPayload(payload);

        AsyncJob saved = asyncJobRepository.save(job);
        log.info("AsyncJob created: id={}, type={}, entity={}:{}", saved.getId(), jobType, relatedEntityType, relatedEntityId);
        return saved;
    }

    public Optional<AsyncJob> getJobById(Long jobId) {
        return asyncJobRepository.findById(jobId);
    }

    public List<AsyncJob> getJobsByEntityId(String entityType, Long entityId) {
        return asyncJobRepository.findByRelatedEntityTypeAndRelatedEntityId(entityType, entityId);
    }
}
