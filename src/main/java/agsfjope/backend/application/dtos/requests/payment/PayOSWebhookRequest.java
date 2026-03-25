package agsfjope.backend.application.dtos.requests.payment;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO đại diện cho dữ liệu webhook PayOS gửi về sau khi giao dịch hoàn tất.
 * <p>
 * PayOS sẽ POST request này vào endpoint webhook của hệ thống khi:
 * - Giao dịch thành công (status = "PAID")
 * - Giao dịch bị hủy hoặc thất bại (status = "CANCELLED")
 * </p>
 * <p>
 * Trường {@code signature} dùng để xác minh rằng request thực sự đến từ PayOS
 * (chống giả mạo webhook). Phải verify trước khi xử lý (BR-42, BR-45).
 * </p>
 */
@Data
@NoArgsConstructor
public class PayOSWebhookRequest {

    /** Mã đơn hàng nội bộ (dùng để tra cứu Payment trong DB). */
    private String code;

    /** Thông điệp mô tả trạng thái giao dịch từ PayOS. */
    private String desc;

    /** Dữ liệu giao dịch chi tiết, bao gồm orderCode, amount, status... */
    private WebhookData data;

    /** Chữ ký HMAC-SHA256 để xác minh tính toàn vẹn của webhook. */
    private String signature;

    /**
     * Dữ liệu giao dịch nằm trong trường {@code data} của webhook PayOS.
     */
    @Data
    @NoArgsConstructor
    public static class WebhookData {

        /** Mã đơn hàng duy nhất do hệ thống tạo ra khi gọi API PayOS tạo link. */
        @JsonProperty("orderCode")
        private Long orderCode;

        /** Số tiền giao dịch (đơn vị: VND). */
        private Long amount;

        /** Mô tả thanh toán. */
        private String description;

        /** ID tài khoản. */
        private String accountNumber;

        /** Thông tin tham chiếu thanh toán. */
        private String reference;

        /** Thời điểm giao dịch (ISO 8601). */
        private String transactionDateTime;

        /** Loại tiền tệ (mặc định "VND"). */
        private String currency;

        /** Mã thanh toán PaymentLinkId. */
        private String paymentLinkId;

        /** Kết quả phản hồi từ ngân hàng. */
        private Integer counterAccountBankId;

        /** Tên ngân hàng đối tác. */
        private String counterAccountBankName;

        /** Tên chủ tài khoản đối tác. */
        private String counterAccountName;

        /** Số tài khoản đối tác. */
        private String counterAccountNumber;

        /** Tên người gửi. */
        private String virtualAccountName;

        /** Tài khoản ảo của PayOS. */
        private String virtualAccountNumber;
    }
}
