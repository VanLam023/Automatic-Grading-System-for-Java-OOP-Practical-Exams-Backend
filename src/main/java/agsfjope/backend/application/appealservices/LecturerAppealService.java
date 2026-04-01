package agsfjope.backend.application.appealservices;

import agsfjope.backend.application.dtos.requests.appeal.ReviewAppealRequest;
import agsfjope.backend.application.dtos.responses.appeal.LecturerAppealDetailResponse;
import agsfjope.backend.application.dtos.responses.appeal.LecturerAppealPageResponse;

import java.util.UUID;

public interface LecturerAppealService {

    /**
     * Lấy danh sách + thống kê tổng quan cho màn Appeal List của giảng viên đang đăng nhập.
     *
     * @param lecturerId UUID giảng viên
     * @param status     (optional) trạng thái để filter
     * @param keyword    (optional) từ khoá tìm kiếm SVC/MSSV
     * @param page       trang hiện tại (0-indexed)
     * @param size       số lượng mỗi trang
     * @return Paged response kèm overview stats
     */
    LecturerAppealPageResponse getAppeals(UUID lecturerId, String status, String keyword, int page, int size);

    /**
     * Lấy chi tiết đơn phúc khảo cho màn Review Page.
     * Yêu cầu kiểm tra quyền: Lecturer chỉ được xem bài phân công cho mình.
     *
     * @param lecturerId UUID giảng viên
     * @param appealId   UUID đơn phúc khảo
     * @return chi tiết đầy đủ để chấm
     */
    LecturerAppealDetailResponse getAppealDetail(UUID lecturerId, UUID appealId);

    /**
     * Nộp báo cáo chấm phúc khảo (Submit Review).
     *
     * @param lecturerId UUID giảng viên
     * @param appealId   UUID đơn phúc khảo
     * @param request    chứa điểm mới, nhận xét
     * @return chi tiết đơn vừa được chấm xong
     */
    LecturerAppealDetailResponse submitReview(UUID lecturerId, UUID appealId, ReviewAppealRequest request);

    /**
     * Tải file bài làm (submission archive) đính kèm trong đơn phúc khảo.
     *
     * @param lecturerId UUID giảng viên
     * @param appealId   UUID đơn phúc khảo
     * @return Luồng dữ liệu file (InputStream)
     */
    java.io.InputStream downloadSubmission(UUID lecturerId, UUID appealId);
}
