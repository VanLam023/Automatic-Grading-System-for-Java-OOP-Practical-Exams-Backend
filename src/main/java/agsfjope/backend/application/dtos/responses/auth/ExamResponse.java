package agsfjope.backend.application.dtos.responses.auth;

import agsfjope.backend.core.enums.ExamStatus;
import agsfjope.backend.core.enums.GradingMode;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class ExamResponse {

    private UUID examId;
    private String name;
    private String semester;
    private String academicYear;
    private String description;
    private OffsetDateTime startTime;
    private OffsetDateTime endTime;
    private ExamStatus status;
    private GradingMode gradingMode;
}
