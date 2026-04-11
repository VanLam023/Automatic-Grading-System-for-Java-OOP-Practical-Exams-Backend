package agsfjope.backend.application.appealservices;

import agsfjope.backend.application.dtos.requests.appeal.AssignAppealRequest;
import agsfjope.backend.application.dtos.responses.appeal.LecturerOptionResponse;
import agsfjope.backend.application.dtos.responses.appeal.StaffAppealDetailResponse;
import agsfjope.backend.application.dtos.responses.appeal.StaffAppealPageResponse;

import java.util.List;
import java.util.UUID;

/**
 * Service interface xử lý các tác vụ Appeal cho Exam Staff.
 */
public interface StaffAppealService {

    /**
     * Lấy danh sách đơn phúc khảo có filter, search và phân trang.
     * Kèm overview stats.
     *
     * @param status  filter theo status (null = tất cả)
     * @param keyword tìm kiếm tên SV, MSSV, tên bài thi
     * @param page    số trang (0-indexed)
     * @param size    số item mỗi trang
     * @return trang danh sách appeal + overview stats
     */
    StaffAppealPageResponse getAppeals(String status, String keyword, String semester, String examName, int page, int size);

    /**
     * Lấy chi tiết một đơn phúc khảo.
     *
     * @param appealId UUID đơn phúc khảo
     * @return toàn bộ thông tin chi tiết
     */
    StaffAppealDetailResponse getAppealDetail(UUID appealId);

    /**
     * Phân công giảng viên cho đơn phúc khảo.
     * Appeal chuyển sang status PROCESSING.
     * Deadline được tính tự động từ SystemConfigs (APPEAL_DEADLINE_DAYS).
     *
     * @param appealId   UUID đơn phúc khảo
     * @param request    chứa lecturerId
     * @param staffId    UUID của staff đang thực hiện phân công
     * @return thông tin appeal sau khi phân công
     */
    StaffAppealDetailResponse assignLecturer(UUID appealId, AssignAppealRequest request, UUID staffId);


    /**
     * Staff hủy đơn phúc khảo đang chờ xử lý.
     *
     * @param appealId id đơn phúc khảo
     * @param staffId  id staff thực hiện
     * @return chi tiết đơn sau khi hủy
     */
    StaffAppealDetailResponse cancelAppeal(UUID appealId, UUID staffId);

    /**
     * Lấy danh sách giảng viên có thể phân công (cho dropdown).
     * Kèm số appeal đang xử lý của từng giảng viên.
     *
     * @return danh sách giảng viên + workload
     */
    List<LecturerOptionResponse> getLecturerOptions();

    /**
     * Xác nhận kết quả phúc khảo từ giảng viên (Approve/Deny).
     *
     * @param appealId id đơn phúc khảo
     * @param request  quyết định (isApprove = true/false)
     * @param staffId  id staff thực hiện
     * @return chi tiết đơn phúc khảo sau khi cập nhật
     */
    StaffAppealDetailResponse confirmAppeal(UUID appealId, agsfjope.backend.application.dtos.requests.appeal.ConfirmAppealRequest request, UUID staffId);

    /**
     * Tải file bài làm (submission archive) đính kèm trong đơn phúc khảo dành cho Exam Staff.
     *
     * @param appealId UUID đơn phúc khảo
     * @return Luồng dữ liệu file (InputStream)
     */
    java.io.InputStream downloadSubmission(UUID appealId);
}
