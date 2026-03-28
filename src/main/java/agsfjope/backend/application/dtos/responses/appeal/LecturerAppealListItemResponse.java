package agsfjope.backend.application.dtos.responses.appeal;

import agsfjope.backend.core.enums.AppealStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LecturerAppealListItemResponse {
    private UUID appealId;
    private String appealCode;

    // Student info
    private String studentName;
    private String studentMssv;

    // Exam info
    private String examName;
    private String semester;
    private String blockName;

    // Appeal info
    private String reason;
    private AppealStatus status;
    private BigDecimal originalScore;
    private BigDecimal newScore;
    
    private OffsetDateTime createdAt;
    private OffsetDateTime deadlineAt;
    
    // Status flags
    private boolean isOverdue;
}
