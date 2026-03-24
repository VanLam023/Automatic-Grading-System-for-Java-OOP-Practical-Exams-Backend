package agsfjope.backend.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Enables Spring's @Async support and configures dedicated thread pools.
 *
 * <p>Two pools are defined:
 * <ul>
 *   <li>{@code taskExecutor}         — general async tasks (email sending, etc.)</li>
 *   <li>{@code gradingTaskExecutor}  — grading pipeline (@Async on GradingService)</li>
 * </ul>
 * </p>
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * General-purpose async executor for tasks like bulk email sending.
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("AsyncEmail-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }

    /**
     * Dedicated thread pool for the grading pipeline.
     *
     * <p>Used by {@link agsfjope.backend.application.gradingservices.GradingService}'s
     * {@code @Async("gradingTaskExecutor")} methods.</p>
     *
     * <h3>Sizing rationale:</h3>
     * <ul>
     *   <li>{@code corePoolSize=2}   — 2 blocks can be graded simultaneously</li>
     *   <li>{@code maxPoolSize=4}    — burst headroom for multiple staff triggers</li>
     *   <li>{@code queueCapacity=10} — small queue; grading should not pile up invisibly</li>
     *   <li>{@code CallerRunsPolicy} — if pool saturated, caller thread runs it
     *       (never silently drops a grading request)</li>
     *   <li>{@code awaitTermination=120s} — waits up to 2 min on shutdown so
     *       in-flight grading doesn't lose data</li>
     * </ul>
     */
    @Bean(name = "gradingTaskExecutor")
    public Executor gradingTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("Grading-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(120);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
