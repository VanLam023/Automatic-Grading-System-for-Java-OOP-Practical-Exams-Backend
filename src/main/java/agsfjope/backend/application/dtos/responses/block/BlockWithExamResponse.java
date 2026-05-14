package agsfjope.backend.application.dtos.responses.block;

import agsfjope.backend.core.enums.ExamStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * DTO gộp thông tin Block và Exam để trả về trong danh sách "Quản lí bài nộp".
 * Được dùng cho endpoint GET /api/staff/blocks (Staff-only).
 */
@Data
@Builder
public class BlockWithExamResponse {

    /** UUID của block */
    private UUID blockId;

    /** UUID của kỳ thi chứa block này */
    private UUID examId;

    /** Tên block — "Block 10" hoặc "Block 3" */
    private String blockName;

    /** Tên kỳ thi (ví dụ: PRO192 PE FA25) */
    private String examName;

    /** Kỳ học (ví dụ: FA25, SP24) */
    private String semester;

    /** Năm học (ví dụ: 2025-2026) */
    private String academicYear;

    /** Ngày thi của block */
    private LocalDate examDate;

    /** Thời điểm mở nộp bài */
    private OffsetDateTime startTime;

    /** Thời điểm đóng nộp bài */
    private OffsetDateTime endTime;

    /** True nếu block đã có đề thi */
    private boolean hasPaper;

    /**
     * Tính trạng thái block dựa trên thời gian hiện tại.
     * Không lưu trong DB — tính động khi serialize.
     */
    @JsonProperty("status")
    public ExamStatus getStatus() {
        if (startTime == null || endTime == null) return ExamStatus.UPCOMING;
        OffsetDateTime now = OffsetDateTime.now();
        if (now.isBefore(startTime)) return ExamStatus.UPCOMING;
        if (!now.isAfter(endTime))   return ExamStatus.ONGOING;
        return ExamStatus.COMPLETED;
    }
}
