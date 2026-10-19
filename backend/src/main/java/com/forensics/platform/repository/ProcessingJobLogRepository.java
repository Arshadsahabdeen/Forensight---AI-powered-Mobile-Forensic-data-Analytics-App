package com.forensics.platform.repository;

import com.forensics.platform.entity.ProcessingJobLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProcessingJobLogRepository extends JpaRepository<ProcessingJobLog, Long> {
    List<ProcessingJobLog> findByJobIdOrderByIdAsc(Long jobId);
}
