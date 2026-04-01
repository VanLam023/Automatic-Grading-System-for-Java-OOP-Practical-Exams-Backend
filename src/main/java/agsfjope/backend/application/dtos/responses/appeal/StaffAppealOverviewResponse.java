package agsfjope.backend.application.dtos.responses.appeal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Overview stats cho trang Appeal Management (Exam Staff).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffAppealOverviewResponse {
    private long total;
    private long pending;
    private long processing;
    private long approved;
    private long denied;
    private long cancelled;
}
