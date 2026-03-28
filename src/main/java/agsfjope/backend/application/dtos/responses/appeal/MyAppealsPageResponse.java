package agsfjope.backend.application.dtos.responses.appeal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response tổng hợp cho trang "Phúc khảo của tôi" (Student).
 *
 * <p>Bao gồm overview stats (tổng, đang xử lý, đã chấp nhận, đã từ chối)
 * và danh sách chi tiết các đơn phúc khảo.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MyAppealsPageResponse {

    // ─── Overview Stats ──────────────────────────────────────────────────────

    /** Tổng số yêu cầu phúc khảo. */
    private long totalAppeals;

    /** Số đơn đang xử lý (PENDING + PROCESSING). */
    private long processingCount;

    /** Số đơn đã chấp nhận (APPROVED). */
    private long approvedCount;

    /** Số đơn đã từ chối (DENIED). */
    private long deniedCount;

    // ─── Appeal List ─────────────────────────────────────────────────────────

    /** Danh sách chi tiết các đơn phúc khảo, sắp xếp theo ngày tạo mới nhất. */
    private List<MyAppealItemResponse> appeals;
}
