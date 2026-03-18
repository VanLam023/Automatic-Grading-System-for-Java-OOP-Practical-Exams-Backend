package agsfjope.backend.configuration.storage;

import io.minio.MinioClient;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình kết nối MinIO Object Storage.
 * <p>
 * Đọc giá trị từ {@code application.yml} dưới prefix {@code minio}.
 * Tạo bean {@link MinioClient} để inject vào các service cần dùng.
 * </p>
 *
 * <pre>
 * # application.yml
 * minio:
 *   endpoint: http://localhost:9000
 *   access-key: minioadmin
 *   secret-key: minioadmin123
 * </pre>
 */
@Configuration
@ConfigurationProperties(prefix = "minio")
@Getter
@Setter
public class MinioConfig {

    /**
     * URL endpoint của MinIO server.
     * Ví dụ: http://localhost:9000 (local Docker)
     * hoặc https://s3.amazonaws.com (khi deploy dùng AWS S3)
     */
    private String endpoint;

    /**
     * Access key (tương đương username).
     * Đặt trong .env: MINIO_ACCESS_KEY=minioadmin
     */
    private String accessKey;

    /**
     * Secret key (tương đương password).
     * Đặt trong .env: MINIO_SECRET_KEY=minioadmin123
     */
    private String secretKey;

    /**
     * Cấu hình tên các buckets.
     */
    private BucketConfig bucket = new BucketConfig();

    /**
     * Tạo MinioClient bean — library chính để gọi API MinIO.
     * Spring tự inject bean này vào MinioService, BucketInitializer, v.v.
     *
     * @return MinioClient đã kết nối
     */
    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    /**
     * Inner class chứa tên các buckets.
     */
    @Getter
    @Setter
    public static class BucketConfig {
        /** Bucket lưu đề thi (.zip) */
        private String examPapers = "exam-papers";
        /** Bucket lưu bài nộp sinh viên (.zip) */
        private String submissions = "submissions";
    }
}
