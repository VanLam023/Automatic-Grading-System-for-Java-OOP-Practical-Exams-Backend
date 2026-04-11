package agsfjope.backend.application.dtos.responses.appeal;

import agsfjope.backend.core.enums.AppealStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Một row trong bảng danh sách appeal (trang Appeal Management của Exam Staff).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffAppealListItemResponse {

    private UUID appealId;
    /** Mã yêu cầu (e.g. #PK-2026-E820) */
    private String appealCode;

    // Student info
    private String studentName;
    private String studentMssv;

    // Exam info
    private String examName;
    private String semester;
    private String blockName;
    private UUID submissionId;

    // Appeal info
    private AppealStatus status;
    private BigDecimal originalScore;
    private BigDecimal newScore;
    private Map<String, BigDecimal> newQuestionScores;
    private OffsetDateTime createdAt;
    private OffsetDateTime deadlineAt;

    // Assignment
    private String assignedLecturerName;
}
