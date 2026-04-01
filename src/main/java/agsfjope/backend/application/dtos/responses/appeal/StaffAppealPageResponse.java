package agsfjope.backend.application.dtos.responses.appeal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response wrapper cho trang Appeal Management (Exam Staff):
 * overview stats + paged danh sách appeals.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffAppealPageResponse {

    private StaffAppealOverviewResponse overview;

    private List<StaffAppealListItemResponse> appeals;
    private int currentPage;
    private int totalPages;
    private long totalElements;
    private int pageSize;
}
