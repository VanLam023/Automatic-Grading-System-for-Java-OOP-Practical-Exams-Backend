package agsfjope.backend.application.dtos.responses.appeal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LecturerAppealOverviewResponse {
    private long totalAssigned;
    private long inReview;   // PROCESSING
    private long completed;  // COMPLETED, APPROVED, DENIED
    private long overdue;    // PROCESSING and deadline passed
}
