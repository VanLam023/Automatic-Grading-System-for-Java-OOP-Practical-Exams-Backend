package agsfjope.backend.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Enables Spring's @Async support and configures a dedicated thread pool
 * for asynchronous tasks (e.g., sending credential emails after bulk import).
 *
 * <p>Without this configuration, @Async methods fall back to
 * {@code SimpleAsyncTaskExecutor} which creates a new thread per call — fine
 * for our volume but this pool provides bounded resources and proper naming for logs.</p>
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Defines the thread pool used for @Async method execution.
     * Pool settings are conservative for a student-count workload (~dozens of emails).
     *
     * @return configured async executor bean
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // Number of threads always kept alive even when idle
        executor.setCorePoolSize(4);
        // Maximum threads allowed when queue is full
        executor.setMaxPoolSize(10);
        // Tasks waiting in queue when all threads are busy
        executor.setQueueCapacity(200);
        // Thread name prefix — makes async threads easy to spot in logs
        executor.setThreadNamePrefix("AsyncEmail-");
        // Wait for running tasks to complete before shutdown (graceful shutdown)
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }
}
