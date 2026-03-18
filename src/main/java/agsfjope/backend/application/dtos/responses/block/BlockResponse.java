package agsfjope.backend.application.dtos.responses.block;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * DTO for returning block information in API responses.
 */
@Data
@Builder
public class BlockResponse {

    /** Unique identifier of the block. */
    private UUID blockId;

    /** UUID of the exam this block belongs to. */
    private UUID examId;

    /** Block name — either "Block 10" or "Block 3" (BR-08). */
    private String name;

    /** Optional description of this block. */
    private String description;

    /** The date on which this block's exam takes place. */
    private LocalDate examDate;

    /** Time when students can start submitting. */
    private OffsetDateTime startTime;

    /** Time when submission closes. */
    private OffsetDateTime endTime;

    /** Timestamp when this block was created (auto-set on exam creation). */
    private OffsetDateTime createdAt;
}
