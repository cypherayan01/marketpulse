package com.marketpulse.jobs;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Runs long jobs one at a time off the request thread, so HTTP callers get an immediate 202. */
@Component
public class BackgroundJobs {

    private static final Logger log = LoggerFactory.getLogger(BackgroundJobs.class);

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public void submit(String name, Runnable job) {
        executor.submit(() -> {
            try {
                job.run();
            } catch (RuntimeException e) {
                log.error("{} failed", name, e);
            }
        });
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}
