package agsfjope.backend.application.dtos.responses.appeal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Một option giảng viên trong dropdown phân công (màn hình Assign Appeal).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LecturerOptionResponse {
    private UUID lecturerId;
    private String fullName;
    private String email;
    /** Số appeal đang xử lý hiện tại (load indicator). */
    private long activeAppealCount;
}
