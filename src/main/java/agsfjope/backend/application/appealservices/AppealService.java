package agsfjope.backend.application.appealservices;

import agsfjope.backend.application.dtos.requests.appeal.CreateAppealRequest;
import agsfjope.backend.application.dtos.responses.appeal.CreateAppealResponse;
import agsfjope.backend.application.dtos.responses.appeal.MyAppealsPageResponse;

import java.util.UUID;

/**
 * Service interface xử lý các tác vụ liên quan đến đơn phúc khảo (Appeal).
 * Tuân theo Clean Architecture: tầng Application định nghĩa interface,
 * tầng Infrastructure (impl) thực hiện.
 */
public interface AppealService {

    /**
     * Tạo đơn phúc khảo mới cho một bài nộp đã được chấm điểm.
     *
     * <p>Luồng xử lý:
     * <ol>
     *   <li>Validate submission (tồn tại, thuộc sinh viên, đã GRADED)</li>
     *   <li>Kiểm tra chưa có appeal nào cho submission này (BR-01)</li>
     *   <li>Tạo Appeal với status {@code PENDING_PAYMENT}</li>
     *   <li>Tạo Payment và gọi PayOS để lấy QR code + checkout URL</li>
     *   <li>Trả về thông tin để Frontend hiển thị màn hình thanh toán</li>
     * </ol>
     * </p>
     *
     * @param studentId UUID của sinh viên đang đăng nhập (lấy từ JWT)
     * @param request   thông tin đơn phúc khảo (submissionId, reason)
     * @return thông tin appeal + link thanh toán PayOS
     */
    CreateAppealResponse createAppeal(UUID studentId, CreateAppealRequest request);

    /**
     * Lấy danh sách tất cả đơn phúc khảo của sinh viên đang đăng nhập.
     * Bao gồm overview stats và chi tiết từng đơn.
     *
     * @param studentId UUID của sinh viên (từ JWT)
     * @return trang My Appeals với stats + danh sách
     */
    MyAppealsPageResponse getMyAppeals(UUID studentId);
}
