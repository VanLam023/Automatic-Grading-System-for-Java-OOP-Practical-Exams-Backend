package agsfjope.backend.application.auditlogservices.impl;

import agsfjope.backend.application.dtos.responses.auditlog.AuditLogResponse;
import agsfjope.backend.core.entities.AuditLog;
import agsfjope.backend.core.entities.Role;
import agsfjope.backend.core.entities.User;
import agsfjope.backend.core.enums.AuditAction;
import agsfjope.backend.core.repositories.auditlog.AuditLogRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit Tests cho AuditLogServiceImpl
 * Pattern: AAA (Arrange - Act - Assert)
 */
@ExtendWith(MockitoExtension.class)
class AuditLogServiceImplTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditLogServiceImpl auditLogService;

    @Captor
    private ArgumentCaptor<AuditLog> auditLogCaptor;

    // =========================================================================
    // getAuditLogs()
    // =========================================================================

    @Test
    @DisplayName("[N] getAuditLogs - Trả về Page AuditLogResponse (Xử lý User == null an toàn)")
    void getAuditLogs_SystemUserNull_ReturnsMappedPage() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        AuditLog auditLog = AuditLog.builder()
                .auditLogId(UUID.randomUUID())
                .action(AuditAction.LOGIN)
                .entityType("Submission")
                .entityId(UUID.randomUUID())
                .user(null) // null user indicates system action
                .ipAddress("127.0.0.1")
                .createdAt(OffsetDateTime.now())
                .build();
        
        Page<AuditLog> mockPage = new PageImpl<>(List.of(auditLog));
        
        when(auditLogRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(mockPage);

        // Act
        Page<AuditLogResponse> result = auditLogService.getAuditLogs(
                null, null, null, null, null, pageable);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        
        AuditLogResponse response = result.getContent().get(0);
        assertEquals(AuditAction.LOGIN.name(), response.getAction());
        assertEquals("Submission", response.getEntityType());
        assertEquals("127.0.0.1", response.getIpAddress());
        assertNull(response.getUsername());
        assertNull(response.getRole());
    }

    @Test
    @DisplayName("[N] getAuditLogs - Map đầy đủ thông tin User và Role")
    void getAuditLogs_WithUserAndRole_MapsUserInformationCorrectly() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        
        Role role = Role.builder().name("ADMIN").build();
        User user = User.builder().username("admin_user").role(role).build();
        
        AuditLog auditLog = AuditLog.builder()
                .auditLogId(UUID.randomUUID())
                .action(AuditAction.CREATE)
                .user(user)
                .build();
        
        when(auditLogRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(auditLog)));

        // Act
        Page<AuditLogResponse> result = auditLogService.getAuditLogs(
                AuditAction.CREATE, "User", UUID.randomUUID(), OffsetDateTime.now(), OffsetDateTime.now(), pageable);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals("admin_user", result.getContent().get(0).getUsername());
        assertEquals("ADMIN", result.getContent().get(0).getRole());
    }

    @Test
    @DisplayName("[B] getAuditLogs - Database trống -> Page rỗng")
    void getAuditLogs_EmptyDatabase_ReturnsEmptyPage() {
        // Arrange
        when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        // Act
        Page<AuditLogResponse> result = auditLogService.getAuditLogs(
                null, null, null, null, null, PageRequest.of(0, 10));

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // =========================================================================
    // getAuditLogById()
    // =========================================================================

    @Test
    @DisplayName("[N] getAuditLogById - Tìm thấy ID hợp lệ -> Trả về Entity map")
    void getAuditLogById_Exists_ReturnsMappedResponse() {
        // Arrange
        UUID logId = UUID.randomUUID();
        AuditLog auditLog = AuditLog.builder()
                .auditLogId(logId)
                .action(AuditAction.UPDATE)
                .oldValues("{\"status\":\"PENDING\"}")
                .newValues("{\"status\":\"APPROVED\"}")
                .build();
                
        when(auditLogRepository.findById(logId)).thenReturn(Optional.of(auditLog));

        // Act
        AuditLogResponse response = auditLogService.getAuditLogById(logId);

        // Assert
        assertNotNull(response);
        assertEquals(logId, response.getAuditLogId());
        assertEquals("UPDATE", response.getAction());
        assertEquals("{\"status\":\"PENDING\"}", response.getOldValues());
        assertEquals("{\"status\":\"APPROVED\"}", response.getNewValues());
    }

    @Test
    @DisplayName("[A] getAuditLogById - ID không tồn tại -> Bắn ngoại lệ EntityNotFoundException")
    void getAuditLogById_NotFound_ThrowsEntityNotFoundException() {
        // Arrange
        UUID logId = UUID.randomUUID();
        when(auditLogRepository.findById(logId)).thenReturn(Optional.empty());

        // Act & Assert
        EntityNotFoundException exception = assertThrows(EntityNotFoundException.class, () -> {
            auditLogService.getAuditLogById(logId);
        });
        
        assertTrue(exception.getMessage().contains("Audit log không tồn tại với id:"));
    }

    // =========================================================================
    // logAction()
    // =========================================================================

    @Test
    @DisplayName("[N] logAction - Có thông tin UserId -> Reference User để lưu DB")
    void logAction_ValidUserId_SavesWithUserReference() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        
        // Act
        auditLogService.logAction(userId, AuditAction.DELETE, "Exam", 
                entityId, null, null, "192.168.1.1", "Mozilla/5.0");

        // Assert
        verify(auditLogRepository).save(auditLogCaptor.capture());
        AuditLog savedLog = auditLogCaptor.getValue();
        
        assertNotNull(savedLog);
        assertEquals(AuditAction.DELETE, savedLog.getAction());
        assertEquals("Exam", savedLog.getEntityType());
        assertEquals(entityId, savedLog.getEntityId());
        assertEquals("192.168.1.1", savedLog.getIpAddress());
        assertEquals("Mozilla/5.0", savedLog.getUserAgent());
        
        assertNotNull(savedLog.getUser());
        assertEquals(userId, savedLog.getUser().getUserId());
    }

    @Test
    @DisplayName("[N] logAction - UserId là null -> Bỏ User reference nhưng log hành động System (như cron job)")
    void logAction_NullUserId_SavesWithoutUserReference() {
        // Arrange
        // Act
        auditLogService.logAction(null, AuditAction.UPDATE, "Submission", 
                null, "{}", "{}", "localhost", "System");

        // Assert
        verify(auditLogRepository).save(auditLogCaptor.capture());
        AuditLog savedLog = auditLogCaptor.getValue();
        
        assertNotNull(savedLog);
        assertNull(savedLog.getUser(), "User reference phải là null khi userId=null");
        assertEquals("localhost", savedLog.getIpAddress());
        assertEquals("System", savedLog.getUserAgent());
        assertEquals("{}", savedLog.getOldValues());
    }

    // =========================================================================
    // cleanupOldLogs()
    // =========================================================================

    @Test
    @DisplayName("[N] cleanupOldLogs - Tính toán cutoff và gọi logic Xóa, trả số record đã xoá")
    void cleanupOldLogs_DeletesOldLogs_ReturnsDeletedCount() {
        // Arrange
        int retentionDays = 30;
        when(auditLogRepository.deleteByCreatedAtBefore(any(OffsetDateTime.class))).thenReturn(150);

        // Act
        int result = auditLogService.cleanupOldLogs(retentionDays);

        // Assert
        assertEquals(150, result);
        verify(auditLogRepository).deleteByCreatedAtBefore(any(OffsetDateTime.class));
    }
}
