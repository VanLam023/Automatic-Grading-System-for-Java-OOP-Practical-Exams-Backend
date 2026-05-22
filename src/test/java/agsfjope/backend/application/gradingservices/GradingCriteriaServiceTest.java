package agsfjope.backend.application.gradingservices;

import agsfjope.backend.application.dtos.requests.grading.GradingCriteriaRequest;
import agsfjope.backend.application.dtos.responses.grading.GradingCriteriaResponse;
import agsfjope.backend.core.entities.GradingCriteria;
import agsfjope.backend.core.entities.Question;
import agsfjope.backend.core.enums.CriterionType;
import agsfjope.backend.core.repositories.exampaper.QuestionRepository;
import agsfjope.backend.core.repositories.grading.GradingCriteriaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests cho GradingCriteriaService — CRUD tiêu chí chấm OOP.
 * Phân loại: [N] Normal, [B] Boundary, [A] Abnormal.
 * Pattern: AAA (Arrange - Act - Assert).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GradingCriteriaService Tests")
class GradingCriteriaServiceTest {

    @Mock private GradingCriteriaRepository criteriaRepo;
    @Mock private QuestionRepository questionRepo;

    @InjectMocks
    private GradingCriteriaService service;

    // ─── Shared fixtures ──────────────────────────────────────────────────────

    private UUID questionId;
    private UUID criteriaId;
    private Question question;
    private GradingCriteria existingCriteria;

    @BeforeEach
    void setUp() {
        questionId = UUID.randomUUID();
        criteriaId = UUID.randomUUID();

        question = Question.builder()
                .questionId(questionId)
                .questionNumber(1)
                .title("Q1 - OOP Design")
                .maxScore(new BigDecimal("10.00"))
                .build();

        existingCriteria = GradingCriteria.builder()
                .criteriaId(criteriaId)
                .question(question)
                .criteriaCode("HAS_ENCAPSULATION")
                .criteriaGroup("STRUCTURAL")
                .criterionType(CriterionType.CLASS_EXISTS)
                .description("Kiểm tra encapsulation")
                .maxScore(new BigDecimal("2.0"))
                .checkParamsJson("{\"className\": \"Student\"}")
                .displayOrder(0)
                .build();
    }

    // =========================================================================
    // listByQuestion()  — [N] 1, [B] 1
    // =========================================================================

    @Test
    @DisplayName("[N] listByQuestion - Câu hỏi có 2 tiêu chí → trả về danh sách sorted by displayOrder")
    void listByQuestion_HasCriteria_ReturnsSortedList() {
        // ── Arrange ──────────────────────────────────────────────────────────
        GradingCriteria c2 = GradingCriteria.builder()
                .criteriaId(UUID.randomUUID())
                .question(question)
                .criteriaCode("HAS_INHERITANCE")
                .criteriaGroup("STRUCTURAL")
                .criterionType(CriterionType.EXTENDS_CHECK)
                .description("Kiểm tra kế thừa")
                .maxScore(new BigDecimal("3.0"))
                .checkParamsJson("{}")
                .displayOrder(1)
                .build();

        when(criteriaRepo.findByQuestion_QuestionIdOrderByDisplayOrderAsc(questionId))
                .thenReturn(List.of(existingCriteria, c2));

        // ── Act ───────────────────────────────────────────────────────────────
        List<GradingCriteriaResponse> result = service.listByQuestion(questionId);

        // ── Assert ────────────────────────────────────────────────────────────
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getCriteriaCode()).isEqualTo("HAS_ENCAPSULATION");
        assertThat(result.get(1).getCriteriaCode()).isEqualTo("HAS_INHERITANCE");
    }

    @Test
    @DisplayName("[B] listByQuestion - Câu hỏi không có tiêu chí → trả về list rỗng")
    void listByQuestion_NoCriteria_ReturnsEmptyList() {
        // ── Arrange ──────────────────────────────────────────────────────────
        when(criteriaRepo.findByQuestion_QuestionIdOrderByDisplayOrderAsc(questionId))
                .thenReturn(List.of());

        // ── Act ───────────────────────────────────────────────────────────────
        List<GradingCriteriaResponse> result = service.listByQuestion(questionId);

        // ── Assert ────────────────────────────────────────────────────────────
        assertThat(result).isEmpty();
    }

    // =========================================================================
    // create()  — [N] 1, [A] 1
    // =========================================================================

    @Test
    @DisplayName("[N] create - Request hợp lệ → lưu và trả về GradingCriteriaResponse")
    void create_ValidRequest_SavesAndReturnsResponse() {
        // ── Arrange ──────────────────────────────────────────────────────────
        GradingCriteriaRequest req = new GradingCriteriaRequest();
        req.setCriteriaCode("HAS_OVERRIDE");
        req.setCriteriaGroup("STRUCTURAL");
        req.setCriterionType(CriterionType.METHOD_SIGNATURE);
        req.setDescription("Kiểm tra @Override");
        req.setMaxScore(new BigDecimal("1.5"));
        req.setCheckParamsJson("{\"annotation\": \"Override\"}");
        req.setDisplayOrder(2);

        when(questionRepo.findById(questionId)).thenReturn(Optional.of(question));
        when(criteriaRepo.save(any(GradingCriteria.class))).thenAnswer(inv -> {
            GradingCriteria gc = inv.getArgument(0);
            // Simulate JPA setting the ID
            try {
                var field = GradingCriteria.class.getDeclaredField("criteriaId");
                field.setAccessible(true);
                field.set(gc, UUID.randomUUID());
            } catch (Exception ignored) {}
            return gc;
        });

        // ── Act ───────────────────────────────────────────────────────────────
        GradingCriteriaResponse response = service.create(questionId, req);

        // ── Assert ────────────────────────────────────────────────────────────
        assertThat(response).isNotNull();
        assertThat(response.getCriteriaCode()).isEqualTo("HAS_OVERRIDE");
        assertThat(response.getMaxScore()).isEqualByComparingTo(new BigDecimal("1.5"));
        verify(criteriaRepo).save(any(GradingCriteria.class));
    }

    @Test
    @DisplayName("[A] create - Question không tồn tại → IllegalArgumentException")
    void create_QuestionNotFound_ThrowsIllegalArgument() {
        // ── Arrange ──────────────────────────────────────────────────────────
        UUID badQuestionId = UUID.randomUUID();
        GradingCriteriaRequest req = new GradingCriteriaRequest();
        req.setCriteriaCode("X");

        when(questionRepo.findById(badQuestionId)).thenReturn(Optional.empty());

        // ── Act & Assert ──────────────────────────────────────────────────────
        assertThatThrownBy(() -> service.create(badQuestionId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Câu hỏi không tồn tại");
        verify(criteriaRepo, never()).save(any());
    }

    // =========================================================================
    // update()  — [N] 1, [A] 2
    // =========================================================================

    @Test
    @DisplayName("[N] update - Request hợp lệ → cập nhật và trả về response mới")
    void update_ValidRequest_UpdatesSuccessfully() {
        // ── Arrange ──────────────────────────────────────────────────────────
        GradingCriteriaRequest req = new GradingCriteriaRequest();
        req.setCriteriaCode("UPDATED_CODE");
        req.setCriterionType(CriterionType.INTERFACE_EXISTS);
        req.setDescription("Updated description");
        req.setMaxScore(new BigDecimal("5.0"));

        when(criteriaRepo.findById(criteriaId)).thenReturn(Optional.of(existingCriteria));
        when(criteriaRepo.save(any(GradingCriteria.class))).thenAnswer(inv -> inv.getArgument(0));

        // ── Act ───────────────────────────────────────────────────────────────
        GradingCriteriaResponse response = service.update(questionId, criteriaId, req);

        // ── Assert ────────────────────────────────────────────────────────────
        assertThat(response).isNotNull();
        assertThat(response.getCriteriaCode()).isEqualTo("UPDATED_CODE");
        assertThat(response.getMaxScore()).isEqualByComparingTo(new BigDecimal("5.0"));
    }

    @Test
    @DisplayName("[A] update - Criteria ID không tồn tại → IllegalArgumentException")
    void update_CriteriaNotFound_ThrowsIllegalArgument() {
        // ── Arrange ──────────────────────────────────────────────────────────
        UUID badCriteriaId = UUID.randomUUID();
        GradingCriteriaRequest req = new GradingCriteriaRequest();

        when(criteriaRepo.findById(badCriteriaId)).thenReturn(Optional.empty());

        // ── Act & Assert ──────────────────────────────────────────────────────
        assertThatThrownBy(() -> service.update(questionId, badCriteriaId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tiêu chí không tồn tại");
    }

    @Test
    @DisplayName("[A] update - Criteria thuộc question khác → IllegalArgumentException")
    void update_WrongQuestion_ThrowsIllegalArgument() {
        // ── Arrange ──────────────────────────────────────────────────────────
        UUID differentQuestionId = UUID.randomUUID();
        GradingCriteriaRequest req = new GradingCriteriaRequest();

        when(criteriaRepo.findById(criteriaId)).thenReturn(Optional.of(existingCriteria));

        // ── Act & Assert ──────────────────────────────────────────────────────
        assertThatThrownBy(() -> service.update(differentQuestionId, criteriaId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tiêu chí không thuộc câu hỏi này");
    }

    // =========================================================================
    // delete()  — [N] 1, [A] 2
    // =========================================================================

    @Test
    @DisplayName("[N] delete - ID hợp lệ → xóa thành công")
    void delete_ValidId_DeletesSuccessfully() {
        // ── Arrange ──────────────────────────────────────────────────────────
        when(criteriaRepo.findById(criteriaId)).thenReturn(Optional.of(existingCriteria));

        // ── Act ───────────────────────────────────────────────────────────────
        service.delete(questionId, criteriaId);

        // ── Assert ────────────────────────────────────────────────────────────
        verify(criteriaRepo).delete(existingCriteria);
    }

    @Test
    @DisplayName("[A] delete - Criteria không tồn tại → IllegalArgumentException")
    void delete_CriteriaNotFound_ThrowsIllegalArgument() {
        // ── Arrange ──────────────────────────────────────────────────────────
        UUID badId = UUID.randomUUID();
        when(criteriaRepo.findById(badId)).thenReturn(Optional.empty());

        // ── Act & Assert ──────────────────────────────────────────────────────
        assertThatThrownBy(() -> service.delete(questionId, badId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tiêu chí không tồn tại");
    }

    @Test
    @DisplayName("[A] delete - Criteria thuộc question khác → IllegalArgumentException")
    void delete_WrongQuestion_ThrowsIllegalArgument() {
        // ── Arrange ──────────────────────────────────────────────────────────
        UUID differentQuestionId = UUID.randomUUID();
        when(criteriaRepo.findById(criteriaId)).thenReturn(Optional.of(existingCriteria));

        // ── Act & Assert ──────────────────────────────────────────────────────
        assertThatThrownBy(() -> service.delete(differentQuestionId, criteriaId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tiêu chí không thuộc câu hỏi này");
    }

    // =========================================================================
    // saveBatch()  — [N] 1, [B] 1
    // =========================================================================

    @Test
    @DisplayName("[N] saveBatch - Thay thế 2 tiêu chí mới → xóa cũ, lưu 2 tiêu chí mới với displayOrder tự động")
    void saveBatch_ValidList_ReplacesAllCriteria() {
        // ── Arrange ──────────────────────────────────────────────────────────
        when(questionRepo.findById(questionId)).thenReturn(Optional.of(question));
        when(criteriaRepo.findByQuestion_QuestionIdOrderByDisplayOrderAsc(questionId))
                .thenReturn(List.of(existingCriteria));
        when(criteriaRepo.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        GradingCriteriaRequest req1 = new GradingCriteriaRequest();
        req1.setCriteriaCode("NEW_CRITERIA_1");
        req1.setCriterionType(CriterionType.CLASS_EXISTS);
        req1.setDescription("New 1");
        req1.setMaxScore(new BigDecimal("3.0"));

        GradingCriteriaRequest req2 = new GradingCriteriaRequest();
        req2.setCriteriaCode("NEW_CRITERIA_2");
        req2.setCriterionType(CriterionType.INTERFACE_EXISTS);
        req2.setDescription("New 2");
        req2.setMaxScore(new BigDecimal("4.0"));

        // ── Act ───────────────────────────────────────────────────────────────
        List<GradingCriteriaResponse> result = service.saveBatch(questionId, List.of(req1, req2));

        // ── Assert ────────────────────────────────────────────────────────────
        assertThat(result).hasSize(2);
        verify(criteriaRepo).deleteAll(List.of(existingCriteria));
        verify(criteriaRepo).flush();
        verify(criteriaRepo).saveAll(argThat(list -> ((List<?>) list).size() == 2));
    }

    @Test
    @DisplayName("[B] saveBatch - Danh sách rỗng → xóa hết tiêu chí cũ, trả về list rỗng")
    void saveBatch_EmptyList_RemovesAllCriteria() {
        // ── Arrange ──────────────────────────────────────────────────────────
        when(questionRepo.findById(questionId)).thenReturn(Optional.of(question));
        when(criteriaRepo.findByQuestion_QuestionIdOrderByDisplayOrderAsc(questionId))
                .thenReturn(List.of(existingCriteria));
        when(criteriaRepo.saveAll(anyList())).thenReturn(List.of());

        // ── Act ───────────────────────────────────────────────────────────────
        List<GradingCriteriaResponse> result = service.saveBatch(questionId, List.of());

        // ── Assert ────────────────────────────────────────────────────────────
        assertThat(result).isEmpty();
        verify(criteriaRepo).deleteAll(List.of(existingCriteria));
        verify(criteriaRepo).flush();
    }
}
