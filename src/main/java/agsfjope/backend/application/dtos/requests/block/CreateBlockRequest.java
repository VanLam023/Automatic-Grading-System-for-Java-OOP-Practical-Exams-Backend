package agsfjope.backend.application.dtos.requests.block;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO for creating a new block within an exam.
 *
 * <p>Exam Staff tự chọn tên block (không giới hạn format).
 * Schedule (examDate, startTime, endTime) sẽ được cập nhật sau qua PUT.</p>
 */
@Data
public class CreateBlockRequest {

    /** Tên block — do Exam Staff tự đặt, phải unique trong cùng 1 exam. */
    @NotBlank(message = "Tên block không được để trống")
    @Size(max = 50, message = "Tên block không được vượt quá 50 ký tự")
    private String name;

    /** Mô tả block (tuỳ chọn). */
    @Size(max = 2000, message = "Mô tả không được vượt quá 2000 ký tự")
    private String description;
}
