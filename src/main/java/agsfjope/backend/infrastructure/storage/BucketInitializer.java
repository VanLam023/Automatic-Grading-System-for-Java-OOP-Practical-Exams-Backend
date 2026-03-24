package agsfjope.backend.infrastructure.storage;

import agsfjope.backend.configuration.storage.MinioConfig;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Tự động tạo các buckets cần thiết khi ứng dụng khởi động.
 * <p>
 * Khi Spring Boot start, {@code @PostConstruct} sẽ chạy và kiểm tra:
 * <ul>
 *   <li>Bucket {@code exam-papers} — lưu đề thi (.zip)</li>
 *   <li>Bucket {@code submissions} — lưu bài nộp sinh viên (.zip)</li>
 * </ul>
 * Nếu bucket chưa tồn tại → tự tạo mới. Nếu đã có → bỏ qua.
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BucketInitializer {

    private final MinioClient minioClient;
    private final MinioConfig minioConfig;

    /**
     * Chạy khi application context khởi tạo xong.
     * Tạo các buckets nếu chưa tồn tại.
     */
    @PostConstruct
    public void initBuckets() {
        createBucketIfNotExists(minioConfig.getBucket().getExamPapers());
        createBucketIfNotExists(minioConfig.getBucket().getSubmissions());
    }

    /**
     * Kiểm tra và tạo bucket nếu chưa tồn tại.
     *
     * @param bucketName tên bucket cần tạo
     */
    private void createBucketIfNotExists(String bucketName) {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder()
                            .bucket(bucketName)
                            .build()
            );

            if (!exists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder()
                                .bucket(bucketName)
                                .build()
                );
                log.info("MinIO: Created bucket '{}'", bucketName);
            } else {
                log.info("MinIO: Bucket '{}' already exists — skipping", bucketName);
            }
        } catch (Exception e) {
            log.warn("MinIO: Could not initialize bucket '{}': {}. " +
                    "App will continue — bucket can be created later.",
                    bucketName, e.getMessage());
        }
    }
}
