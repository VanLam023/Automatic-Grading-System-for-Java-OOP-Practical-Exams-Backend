package agsfjope.backend.infrastructure.storage;

import io.minio.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * Service wrapper cho MinIO Java SDK.
 * <p>
 * Cung cấp các thao tác cơ bản với file:
 * <ul>
 *   <li>{@link #uploadFile}    — upload file lên bucket</li>
 *   <li>{@link #downloadFile}  — download file từ bucket (trả InputStream)</li>
 *   <li>{@link #deleteFile}    — xóa file khỏi bucket</li>
 *   <li>{@link #getPresignedUrl} — tạo URL tạm thời có thời hạn</li>
 *   <li>{@link #fileExists}    — kiểm tra file tồn tại</li>
 * </ul>
 * </p>
 *
 * <p>Các service khác (ExamPaperService, SubmissionService) sẽ gọi MinioService
 * thay vì gọi thẳng MinIO SDK.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MinioService {

    private final MinioClient minioClient;

    // ─── UPLOAD ──────────────────────────────────────────────────────────

    /**
     * Upload file lên MinIO bucket.
     *
     * @param bucketName tên bucket (vd: "exam-papers", "submissions")
     * @param objectName đường dẫn file trong bucket (vd: "exams/uuid/đề.zip")
     * @param inputStream nội dung file
     * @param contentType MIME type (vd: "application/zip")
     * @param fileSize    kích thước file tính bằng bytes
     */
    public void uploadFile(String bucketName, String objectName,
                           InputStream inputStream, String contentType, long fileSize) {
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(inputStream, fileSize, -1)
                            .contentType(contentType)
                            .build()
            );
            log.info("MinIO: Uploaded {} to bucket '{}'", objectName, bucketName);
        } catch (Exception e) {
            log.error("MinIO: Failed to upload {} to '{}': {}",
                    objectName, bucketName, e.getMessage());
            throw new RuntimeException("Không thể upload file: " + e.getMessage(), e);
        }
    }

    // ─── DOWNLOAD ────────────────────────────────────────────────────────

    /**
     * Download file từ MinIO bucket.
     *
     * @param bucketName tên bucket
     * @param objectName đường dẫn file trong bucket
     * @return InputStream chứa nội dung file — caller phải tự đóng
     */
    public InputStream downloadFile(String bucketName, String objectName) {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );
        } catch (Exception e) {
            log.error("MinIO: Failed to download {} from '{}': {}",
                    objectName, bucketName, e.getMessage());
            throw new RuntimeException("Không thể download file: " + e.getMessage(), e);
        }
    }

    // ─── DELETE ──────────────────────────────────────────────────────────

    /**
     * Xóa file khỏi MinIO bucket.
     *
     * @param bucketName tên bucket
     * @param objectName đường dẫn file trong bucket
     */
    public void deleteFile(String bucketName, String objectName) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );
            log.info("MinIO: Deleted {} from bucket '{}'", objectName, bucketName);
        } catch (Exception e) {
            log.error("MinIO: Failed to delete {} from '{}': {}",
                    objectName, bucketName, e.getMessage());
            throw new RuntimeException("Không thể xóa file: " + e.getMessage(), e);
        }
    }

    // ─── PRESIGNED URL ───────────────────────────────────────────────────

    /**
     * Tạo URL tạm thời để download file (có thời hạn).
     * <p>
     * URL này có thể gửi cho client/browser để download trực tiếp
     * từ MinIO mà không cần đi qua Spring Boot.
     * </p>
     *
     * @param bucketName    tên bucket
     * @param objectName    đường dẫn file trong bucket
     * @param expirySeconds thời hạn URL (giây), tối đa 7 ngày = 604800s
     * @return presigned URL dạng string
     */
    public String getPresignedUrl(String bucketName, String objectName, int expirySeconds) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucketName)
                            .object(objectName)
                            .expiry(expirySeconds, TimeUnit.SECONDS)
                            .build()
            );
        } catch (Exception e) {
            log.error("MinIO: Failed to generate presigned URL for {}/{}: {}",
                    bucketName, objectName, e.getMessage());
            throw new RuntimeException("Không thể tạo URL download: " + e.getMessage(), e);
        }
    }

    // ─── COPY ────────────────────────────────────────────────────────────────

    /**
     * Sao chép object trong cùng một bucket sang một key mới.
     * Dùng native MinIO copy API — không cần download/reupload.
     *
     * @param bucketName   tên bucket (source và destination giống nhau)
     * @param sourceObject đường dẫn nguồn trong bucket
     * @param destObject   đường dẫn đích trong bucket
     */
    public void copyObject(String bucketName, String sourceObject, String destObject) {
        try {
            minioClient.copyObject(
                    CopyObjectArgs.builder()
                            .bucket(bucketName)
                            .object(destObject)
                            .source(CopySource.builder()
                                    .bucket(bucketName)
                                    .object(sourceObject)
                                    .build())
                            .build()
            );
            log.info("MinIO: Copied '{}' → '{}' in bucket '{}'", sourceObject, destObject, bucketName);
        } catch (Exception e) {
            log.error("MinIO: Failed to copy '{}' → '{}': {}", sourceObject, destObject, e.getMessage());
            throw new RuntimeException("Không thể copy object trong MinIO: " + e.getMessage(), e);
        }
    }

    // ─── FILE EXISTS ─────────────────────────────────────────────────────────

    /**
     * Kiểm tra file có tồn tại trong bucket không.
     *
     * @param bucketName tên bucket
     * @param objectName đường dẫn file
     * @return true nếu file tồn tại, false nếu không
     */
    public boolean fileExists(String bucketName, String objectName) {
        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
