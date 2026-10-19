package com.forensics.platform.service;

import com.forensics.platform.ufdr.UFDRService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/**
 * Hands processing jobs to a small bounded worker pool. With {@code app.processing.async=false}
 * (used by tests) jobs run synchronously on the calling thread.
 */
@Component
public class ProcessingJobRunner {

    private final UFDRService processor;
    private final TaskExecutor executor;

    public ProcessingJobRunner(
            UFDRService processor,
            @Value("${app.processing.async:true}") boolean async,
            @Value("${app.processing.workers:2}") int workers
    ) {
        this.processor = processor;
        if (async) {
            ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
            pool.setCorePoolSize(Math.max(1, workers));
            pool.setMaxPoolSize(Math.max(1, workers));
            pool.setQueueCapacity(500);
            pool.setThreadNamePrefix("evidence-processing-");
            pool.setWaitForTasksToCompleteOnShutdown(false);
            pool.initialize();
            this.executor = pool;
        } else {
            this.executor = new SyncTaskExecutor();
        }
    }

    public void submit(Long jobId) {
        executor.execute(() -> processor.process(jobId));
    }
}
