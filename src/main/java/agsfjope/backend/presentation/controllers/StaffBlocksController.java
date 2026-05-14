package agsfjope.backend.presentation.controllers;

import agsfjope.backend.application.dtos.responses.block.BlockWithExamResponse;
import agsfjope.backend.core.entities.Block;
import agsfjope.backend.core.repositories.block.BlockRepository;
import agsfjope.backend.core.repositories.exampaper.ExamPaperRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * API endpoint dành riêng cho Staff: lấy toàn bộ danh sách Block kèm thông tin Exam
 * trong một lần gọi duy nhất.
 *
 * <p>Endpoint: {@code GET /api/staff/blocks}</p>
 *
 * <p>Được dùng cho trang "Quản lí bài nộp" trên sidebar Staff,
 * cho phép Staff chọn Kỳ học + Block từ một dropdown duy nhất
 * mà không cần điều hướng qua từng kỳ thi.</p>
 */
@RestController
@RequiredArgsConstructor
public class StaffBlocksController {

    private final BlockRepository      blockRepository;
    private final ExamPaperRepository  examPaperRepository;

    private static final String STAFF_ROLES =
            "hasAnyAuthority('EXAM_STAFF','ROLE_EXAM_STAFF','SYSTEM_ADMIN','ROLE_SYSTEM_ADMIN')";

    /**
     * Trả về toàn bộ Block (có kèm thông tin Exam) sắp xếp theo:
     * Semester DESC → Exam name ASC → Block name ASC.
     *
     * <p>Chỉ trả về block của các kỳ thi CHƯA bị xóa (deletedAt IS NULL).</p>
     */
    @GetMapping("/api/staff/blocks")
    @PreAuthorize(STAFF_ROLES)
    public ResponseEntity<Map<String, Object>> getAllBlocksWithExam() {
        List<Block> blocks = blockRepository.findAllWithExamOrderBySemesterDesc();

        List<BlockWithExamResponse> data = blocks.stream()
                .filter(b -> b.getExam() != null && b.getExam().getDeletedAt() == null)
                .map(this::mapToResponse)
                .toList();

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Danh sách block: " + data.size() + " block.");
        response.put("data", data);
        response.put("errors", null);
        return ResponseEntity.ok(response);
    }

    // ─── MAPPING ──────────────────────────────────────────────────────────

    private BlockWithExamResponse mapToResponse(Block block) {
        boolean hasPaper = examPaperRepository.existsByBlock_BlockId(block.getBlockId());
        var exam = block.getExam();
        return BlockWithExamResponse.builder()
                .blockId(block.getBlockId())
                .examId(exam.getExamId())
                .blockName(block.getName())
                .examName(exam.getName())
                .semester(exam.getSemester())
                .academicYear(exam.getAcademicYear())
                .examDate(block.getExamDate())
                .startTime(block.getStartTime())
                .endTime(block.getEndTime())
                .hasPaper(hasPaper)
                .build();
    }
}
