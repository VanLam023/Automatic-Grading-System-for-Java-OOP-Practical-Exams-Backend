package agsfjope.backend.application.dtos.responses.appeal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LecturerAppealPageResponse {
    private LecturerAppealOverviewResponse overview;
    private List<LecturerAppealListItemResponse> appeals;
    
    private int currentPage;
    private int totalPages;
    private long totalElements;
    private int pageSize;
}
