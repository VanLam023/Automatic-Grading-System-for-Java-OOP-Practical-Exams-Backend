package agsfjope.backend.configuration.storage;

import io.minio.MinioClient;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

/**
 * Cấu hình kết nối Object Storage.
 * <p>
 * Tạo 2 bộ beans song song:
 * <ul>
 *   <li>{@link MinioClient} — cho MinIO local / ngrok tunnel (dùng bởi {@code MinioService})</li>
 *   <li>{@link S3Client} + {@link S3Presigner} — cho Supabase S3 (dùng bởi {@code SupabaseStorageService})</li>
 * </ul>
 * </p>
 *
 * <pre>
 * # .env MinIO (local / ngrok)
 * MINIO_ENDPOINT=http://localhost:9000
 * MINIO_ACCESS_KEY=minioadmin
 * MINIO_SECRET_KEY=minioadmin123
 *
 * # .env Supabase S3
 * SUPABASE_S3_ENDPOINT=https://&lt;project-id&gt;.supabase.co/storage/v1/s3
 * SUPABASE_S3_ACCESS_KEY=&lt;access-key-id&gt;
 * SUPABASE_S3_SECRET_KEY=&lt;secret-access-key&gt;
 * SUPABASE_S3_REGION=ap-southeast-1
 * </pre>
 */
@Configuration
@ConfigurationProperties(prefix = "minio")
@Getter
@Setter
public class MinioConfig {

    // ─── MinIO properties ─────────────────────────────────────────────────────

    /** URL endpoint MinIO (local hoặc ngrok). */
    private String endpoint;

    /** Access Key MinIO. */
    private String accessKey;

    /** Secret Key MinIO. */
    private String secretKey;

    /** Region (không bắt buộc với MinIO local). */
    private String region;

    /** Cấu hình tên buckets. */
    private BucketConfig bucket = new BucketConfig();

    // ─── Supabase S3 properties ───────────────────────────────────────────────

    /** Nested Supabase S3 config block — prefix: minio.supabase */
    private SupabaseS3Config supabase = new SupabaseS3Config();

    // ─── BEANS: MinIO ─────────────────────────────────────────────────────────

    /**
     * MinioClient bean — dùng cho MinIO local (hoặc ngrok tunnel).
     * Inject vào {@link agsfjope.backend.infrastructure.storage.MinioService}.
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

    // ─── BEANS: Supabase S3 (AWS SDK v2) ─────────────────────────────────────

    /**
     * AWS SDK v2 S3Client bean — dùng cho Supabase Storage.
     * Kích hoạt {@code pathStyleAccessEnabled(true)} để tương thích endpoint có path prefix
     * ({@code /storage/v1/s3}) của Supabase.
     * Inject vào {@link agsfjope.backend.infrastructure.storage.SupabaseStorageService}.
     *
     * @return S3Client đã cấu hình
     */
    @Bean
    public S3Client s3Client() {
        Region awsRegion = (supabase.region != null && !supabase.region.isBlank())
                ? Region.of(supabase.region)
                : Region.US_EAST_1;

        return S3Client.builder()
                .endpointOverride(URI.create(supabase.endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(supabase.accessKey, supabase.secretKey)))
                .region(awsRegion)
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }
    // ─── INNER CLASSES ────────────────────────────────────────────────────────

    /**
     * Cấu hình tên buckets dùng chung cho cả MinIO và Supabase.
     */
    @Getter
    @Setter
    public static class BucketConfig {
        /** Bucket lưu đề thi (.zip). */
        private String examPapers = "exam-papers";
        /** Bucket lưu bài nộp sinh viên (.zip). */
        private String submissions = "submissions";
    }

    /**
     * Nested config cho Supabase S3 — ánh xạ từ prefix {@code minio.supabase.*}.
     */
    @Getter
    @Setter
    public static class SupabaseS3Config {
        /** Supabase S3 endpoint. VD: {@code https://xxx.supabase.co/storage/v1/s3} */
        private String endpoint = "https://localhost";
        private String accessKey = "";
        private String secretKey = "";
        private String region    = "ap-southeast-1";
    }
}
