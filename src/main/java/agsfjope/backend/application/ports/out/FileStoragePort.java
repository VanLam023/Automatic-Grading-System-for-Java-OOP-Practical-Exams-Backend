package agsfjope.backend.application.ports.out;

import java.io.InputStream;

/**
 * Output port cho Object Storage (Clean Architecture).
 *
 * <p>Triển khai: {@code MinioService} và {@code SupabaseStorageService}.
 * Dùng {@code @Primary} trên implementation đang active để Spring inject đúng bean.</p>
 *
 * <ul>
 *   <li>{@link #uploadFile}      — upload file lên bucket</li>
 *   <li>{@link #downloadFile}    — download file từ bucket</li>
 *   <li>{@link #deleteFile}      — xóa file khỏi bucket</li>
 *   <li>{@link #getPresignedUrl} — tạo presigned URL download</li>
 *   <li>{@link #copyObject}      — sao chép object trong cùng bucket</li>
 *   <li>{@link #fileExists}      — kiểm tra file tồn tại</li>
 * </ul>
 */
public interface FileStoragePort {

    /**
     * Upload file lên bucket.
     *
     * @param bucketName  tên bucket (vd: "exam-papers", "submissions")
     * @param objectName  đường dẫn file trong bucket
     * @param inputStream nội dung file
     * @param contentType MIME type (vd: "application/zip")
     * @param fileSize    kích thước file tính bằng bytes
     */
    void uploadFile(String bucketName, String objectName,
                    InputStream inputStream, String contentType, long fileSize);

    /**
     * Download file từ bucket.
     *
     * @param bucketName tên bucket
     * @param objectName đường dẫn file trong bucket
     * @return InputStream chứa nội dung file — caller phải tự đóng
     */
    InputStream downloadFile(String bucketName, String objectName);

    /**
     * Xóa file khỏi bucket.
     *
     * @param bucketName tên bucket
     * @param objectName đường dẫn file trong bucket
     */
    void deleteFile(String bucketName, String objectName);

    /**
     * Tạo URL tạm thời để download file.
     *
     * @param bucketName    tên bucket
     * @param objectName    đường dẫn file trong bucket
     * @param expirySeconds thời hạn URL (giây), tối đa 7 ngày = 604800s
     * @return presigned URL dạng string
     */
    String getPresignedUrl(String bucketName, String objectName, int expirySeconds);

    /**
     * Sao chép object trong cùng một bucket sang key mới.
     *
     * @param bucketName   tên bucket
     * @param sourceObject đường dẫn nguồn
     * @param destObject   đường dẫn đích
     */
    void copyObject(String bucketName, String sourceObject, String destObject);

    /**
     * Kiểm tra file có tồn tại trong bucket không.
     *
     * @param bucketName tên bucket
     * @param objectName đường dẫn file
     * @return true nếu file tồn tại
     */
    boolean fileExists(String bucketName, String objectName);
}
