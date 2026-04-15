package agsfjope.backend.infrastructure.storage;

import agsfjope.backend.application.ports.out.FileStoragePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.InputStream;

/**
 * Service wrapper cho Supabase Storage (AWS SDK v2 S3Client).
 * <p>
 * Cung cấp các thao tác file tương tự {@link MinioService} nhưng dùng
 * AWS SDK v2 để kết nối Supabase S3 (endpoint có path {@code /storage/v1/s3}).
 * </p>
 * <p>
 * Cấu hình qua {@code minio.supabase.*} trong {@code application.yml}.
 * </p>
 *
 * <ul>
 *   <li>{@link #uploadFile}      — upload file lên Supabase bucket</li>
 *   <li>{@link #downloadFile}    — download file từ Supabase bucket</li>
 *   <li>{@link #deleteFile}      — xóa file khỏi Supabase bucket</li>
 *   <li>{@link #getPresignedUrl} — tạo presigned URL download</li>
 *   <li>{@link #copyObject}      — sao chép object trong cùng bucket</li>
 *   <li>{@link #fileExists}      — kiểm tra file tồn tại</li>
 * </ul>
 */
@Slf4j
@Primary
@Service
@RequiredArgsConstructor
public class SupabaseStorageService implements FileStoragePort {

    private final S3Client s3Client;

    // ─── UPLOAD ──────────────────────────────────────────────────────────────

    /**
     * Upload file lên Supabase Storage bucket.
     *
     * @param bucketName  tên bucket (vd: "exam-papers", "submissions")
     * @param objectName  đường dẫn file trong bucket (vd: "exams/uuid/de.zip")
     * @param inputStream nội dung file
     * @param contentType MIME type (vd: "application/zip")
     * @param fileSize    kích thước file tính bằng bytes
     */
    public void uploadFile(String bucketName, String objectName,
                           InputStream inputStream, String contentType, long fileSize) {
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectName)
                    .contentType(contentType)
                    .contentLength(fileSize)
                    .build();

            s3Client.putObject(request, RequestBody.fromInputStream(inputStream, fileSize));
            log.info("Supabase: Uploaded {} to bucket '{}'", objectName, bucketName);
        } catch (Exception e) {
            log.error("Supabase: Failed to upload {} to '{}': {}", objectName, bucketName, e.getMessage());
            throw new RuntimeException("Không thể upload file lên Supabase: " + e.getMessage(), e);
        }
    }

    // ─── DOWNLOAD ────────────────────────────────────────────────────────────

    /**
     * Download file từ Supabase Storage bucket.
     *
     * @param bucketName tên bucket
     * @param objectName đường dẫn file trong bucket
     * @return InputStream chứa nội dung file — caller phải tự đóng
     */
    public InputStream downloadFile(String bucketName, String objectName) {
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectName)
                    .build();

            ResponseInputStream<GetObjectResponse> response = s3Client.getObject(request);
            log.info("Supabase: Downloaded {} from bucket '{}'", objectName, bucketName);
            return response;
        } catch (Exception e) {
            log.error("Supabase: Failed to download {} from '{}': {}", objectName, bucketName, e.getMessage());
            throw new RuntimeException("Không thể download file từ Supabase: " + e.getMessage(), e);
        }
    }

    // ─── DELETE ──────────────────────────────────────────────────────────────

    /**
     * Xóa file khỏi Supabase Storage bucket.
     *
     * @param bucketName tên bucket
     * @param objectName đường dẫn file trong bucket
     */
    public void deleteFile(String bucketName, String objectName) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectName)
                    .build());
            log.info("Supabase: Deleted {} from bucket '{}'", objectName, bucketName);
        } catch (Exception e) {
            log.error("Supabase: Failed to delete {} from '{}': {}", objectName, bucketName, e.getMessage());
            throw new RuntimeException("Không thể xóa file trên Supabase: " + e.getMessage(), e);
        }
    }

    // ─── PRESIGNED URL ───────────────────────────────────────────────────────

    /**
     * Presigned URL — chưa hỗ trợ cho Supabase (S3Presigner với path-style cần cấu hình thêm).
     * Dùng Supabase Storage REST API để tạo signed URL nếu cần.
     *
     * @throws UnsupportedOperationException luôn luôn
     */
    public String getPresignedUrl(String bucketName, String objectName, int expirySeconds) {
        throw new UnsupportedOperationException(
                "Presigned URL cho Supabase chưa được hỗ trợ. " +
                "Dùng Supabase REST API /storage/v1/object/sign để tạo signed URL.");
    }

    // ─── COPY ────────────────────────────────────────────────────────────────

    /**
     * Sao chép object trong cùng bucket sang key mới.
     *
     * @param bucketName   tên bucket
     * @param sourceObject đường dẫn nguồn
     * @param destObject   đường dẫn đích
     */
    public void copyObject(String bucketName, String sourceObject, String destObject) {
        try {
            s3Client.copyObject(CopyObjectRequest.builder()
                    .sourceBucket(bucketName)
                    .sourceKey(sourceObject)
                    .destinationBucket(bucketName)
                    .destinationKey(destObject)
                    .build());
            log.info("Supabase: Copied '{}' → '{}' in bucket '{}'", sourceObject, destObject, bucketName);
        } catch (Exception e) {
            log.error("Supabase: Failed to copy '{}' → '{}': {}", sourceObject, destObject, e.getMessage());
            throw new RuntimeException("Không thể copy object trên Supabase: " + e.getMessage(), e);
        }
    }

    // ─── FILE EXISTS ─────────────────────────────────────────────────────────

    /**
     * Kiểm tra file có tồn tại trong Supabase bucket không.
     *
     * @param bucketName tên bucket
     * @param objectName đường dẫn file
     * @return true nếu file tồn tại, false nếu không
     */
    public boolean fileExists(String bucketName, String objectName) {
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectName)
                    .build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (Exception e) {
            log.warn("Supabase: Could not check existence of {}/{}: {}", bucketName, objectName, e.getMessage());
            return false;
        }
    }
}
