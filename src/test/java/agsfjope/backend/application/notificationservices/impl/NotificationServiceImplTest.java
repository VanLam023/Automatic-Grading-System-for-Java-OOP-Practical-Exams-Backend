package agsfjope.backend.application.notificationservices.impl;

import agsfjope.backend.application.dtos.responses.notification.NotificationResponse;
import agsfjope.backend.application.dtos.responses.notification.UnreadCountResponse;
import agsfjope.backend.core.entities.Notification;
import agsfjope.backend.core.entities.User;
import agsfjope.backend.core.exceptions.notification.NotificationNotFoundException;
import agsfjope.backend.core.repositories.notification.NotificationRepository;
import agsfjope.backend.infrastructure.security.SecurityUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

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
 * Unit Tests cho NotificationServiceImpl
 * Pattern: AAA (Arrange - Act - Assert)
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    @Captor
    private ArgumentCaptor<Notification> notificationCaptor;

    @Captor
    private ArgumentCaptor<List<Notification>> notificationListCaptor;

    private MockedStatic<SecurityUtils> mockedSecurityUtils;
    private User mockCurrentUser;
    private UUID currentUserId;

    @BeforeEach
    void setUp() {
        currentUserId = UUID.randomUUID();
        mockCurrentUser = User.builder().userId(currentUserId).username("mock_user").build();
        
        mockedSecurityUtils = mockStatic(SecurityUtils.class);
        // Default mock for getCurrentUser
        mockedSecurityUtils.when(SecurityUtils::getCurrentUser).thenReturn(mockCurrentUser);
    }

    @AfterEach
    void tearDown() {
        mockedSecurityUtils.close();
    }

    // =========================================================================
    // getMyNotifications()
    // =========================================================================

    @Test
    @DisplayName("[N] getMyNotifications - filter 'unread' -> Trả về danh sách NotificationResponse")
    void getMyNotifications_FilterUnread_ReturnsUnreadList() {
        // Arrange
        Notification notif = Notification.builder()
                .notificationId(UUID.randomUUID())
                .isRead(false)
                .title("Unread Title")
                .build();
        when(notificationRepository.findByUser_UserIdAndIsReadOrderByCreatedAtDesc(currentUserId, false))
                .thenReturn(List.of(notif));

        // Act
        List<NotificationResponse> result = notificationService.getMyNotifications("unread");

        // Assert
        assertEquals(1, result.size());
        assertEquals("Unread Title", result.get(0).getTitle());
        assertFalse(result.get(0).getIsRead());
        verify(notificationRepository).findByUser_UserIdAndIsReadOrderByCreatedAtDesc(currentUserId, false);
    }

    @Test
    @DisplayName("[N] getMyNotifications - filter 'read' -> Trả về danh sách NotificationResponse")
    void getMyNotifications_FilterRead_ReturnsReadList() {
        // Arrange
        Notification notif = Notification.builder()
                .notificationId(UUID.randomUUID())
                .isRead(true)
                .title("Read Title")
                .build();
        when(notificationRepository.findByUser_UserIdAndIsReadOrderByCreatedAtDesc(currentUserId, true))
                .thenReturn(List.of(notif));

        // Act
        List<NotificationResponse> result = notificationService.getMyNotifications("read");

        // Assert
        assertEquals(1, result.size());
        assertEquals("Read Title", result.get(0).getTitle());
        assertTrue(result.get(0).getIsRead());
        verify(notificationRepository).findByUser_UserIdAndIsReadOrderByCreatedAtDesc(currentUserId, true);
    }

    @Test
    @DisplayName("[N] getMyNotifications - filter 'all' -> Trả về tất cả NotificationResponse")
    void getMyNotifications_FilterAll_ReturnsAllList() {
        // Arrange
        Notification notif1 = Notification.builder().notificationId(UUID.randomUUID()).build();
        Notification notif2 = Notification.builder().notificationId(UUID.randomUUID()).build();
        when(notificationRepository.findByUser_UserIdOrderByCreatedAtDesc(currentUserId))
                .thenReturn(List.of(notif1, notif2));

        // Act
        List<NotificationResponse> result = notificationService.getMyNotifications("all");

        // Assert
        assertEquals(2, result.size());
        verify(notificationRepository).findByUser_UserIdOrderByCreatedAtDesc(currentUserId);
    }

    @Test
    @DisplayName("[B] getMyNotifications - Database rỗng -> Trả về list rỗng")
    void getMyNotifications_EmptyDatabase_ReturnsEmptyList() {
        // Arrange
        when(notificationRepository.findByUser_UserIdOrderByCreatedAtDesc(currentUserId))
                .thenReturn(Collections.emptyList());

        // Act
        List<NotificationResponse> result = notificationService.getMyNotifications("any_other");

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // =========================================================================
    // getUnreadCount()
    // =========================================================================

    @Test
    @DisplayName("[N] getUnreadCount - Có thông báo chưa đọc -> Trả về count chính xác")
    void getUnreadCount_HasUnread_ReturnsCount() {
        // Arrange
        when(notificationRepository.countByUser_UserIdAndIsRead(currentUserId, false)).thenReturn(5L);

        // Act
        UnreadCountResponse result = notificationService.getUnreadCount();

        // Assert
        assertEquals(5L, result.getUnreadCount());
    }

    @Test
    @DisplayName("[N] getUnreadCount - Không có thông báo chưa đọc -> Trả về 0")
    void getUnreadCount_NoUnread_ReturnsZero() {
        // Arrange
        when(notificationRepository.countByUser_UserIdAndIsRead(currentUserId, false)).thenReturn(0L);

        // Act
        UnreadCountResponse result = notificationService.getUnreadCount();

        // Assert
        assertEquals(0L, result.getUnreadCount());
    }

    // =========================================================================
    // markAsRead()
    // =========================================================================

    @Test
    @DisplayName("[N] markAsRead - Notification đang unread -> Đánh dấu là read và lưu vào DB")
    void markAsRead_IsUnread_SetsTrueAndSaves() {
        // Arrange
        UUID notificationId = UUID.randomUUID();
        Notification notification = Notification.builder()
                .notificationId(notificationId)
                .isRead(false)
                .build();
                
        when(notificationRepository.findByNotificationIdAndUser_UserId(notificationId, currentUserId))
                .thenReturn(Optional.of(notification));

        // Act
        notificationService.markAsRead(notificationId);

        // Assert
        verify(notificationRepository).save(notificationCaptor.capture());
        Notification saved = notificationCaptor.getValue();
        assertTrue(saved.getIsRead());
        assertNotNull(saved.getReadAt());
    }

    @Test
    @DisplayName("[B] markAsRead - Notification CÓ isRead=true -> Bỏ qua lệnh save (tối ưu DB)")
    void markAsRead_AlreadyRead_SkipsSave() {
        // Arrange
        UUID notificationId = UUID.randomUUID();
        Notification notification = Notification.builder()
                .notificationId(notificationId)
                .isRead(true)
                .build();
                
        when(notificationRepository.findByNotificationIdAndUser_UserId(notificationId, currentUserId))
                .thenReturn(Optional.of(notification));

        // Act
        notificationService.markAsRead(notificationId);

        // Assert
        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("[A] markAsRead - Không tìm thấy notification hoặc không thuộc user -> Ném Exception")
    void markAsRead_NotFound_ThrowsException() {
        // Arrange
        UUID notificationId = UUID.randomUUID();
        when(notificationRepository.findByNotificationIdAndUser_UserId(notificationId, currentUserId))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(NotificationNotFoundException.class, () -> {
            notificationService.markAsRead(notificationId);
        });
        
        verify(notificationRepository, never()).save(any());
    }

    // =========================================================================
    // markAllAsRead()
    // =========================================================================

    @Test
    @DisplayName("[N] markAllAsRead - Có nhiều unread -> Đánh dấu tất cả là read và lưu DB một lần")
    void markAllAsRead_HasUnreadList_SetsAllTrueAndSavesAll() {
        // Arrange
        Notification n1 = Notification.builder().isRead(false).build();
        Notification n2 = Notification.builder().isRead(false).build();
        
        when(notificationRepository.findByUser_UserIdAndIsReadOrderByCreatedAtDesc(currentUserId, false))
                .thenReturn(List.of(n1, n2));

        // Act
        notificationService.markAllAsRead();

        // Assert
        verify(notificationRepository).saveAll(notificationListCaptor.capture());
        List<Notification> savedList = notificationListCaptor.getValue();
        
        assertEquals(2, savedList.size());
        assertTrue(savedList.get(0).getIsRead());
        assertNotNull(savedList.get(0).getReadAt());
        assertTrue(savedList.get(1).getIsRead());
        assertNotNull(savedList.get(1).getReadAt());
    }

    @Test
    @DisplayName("[B] markAllAsRead - Không có notification unread nào -> Return ngay, không save DB")
    void markAllAsRead_EmptyUnread_SkipsSaveAll() {
        // Arrange
        when(notificationRepository.findByUser_UserIdAndIsReadOrderByCreatedAtDesc(currentUserId, false))
                .thenReturn(Collections.emptyList());

        // Act
        notificationService.markAllAsRead();

        // Assert
        verify(notificationRepository, never()).saveAll(any());
    }

    // =========================================================================
    // createNotification()
    // =========================================================================

    @Test
    @DisplayName("[N] createNotification - Lưu mới notification thành công vào DB")
    void createNotification_ValidData_SavesNewNotification() {
        // Arrange
        UUID targetUserId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        
        // Act
        notificationService.createNotification(
                targetUserId, "Title", "Body Msg", "EXAM", entityId);

        // Assert
        verify(notificationRepository).save(notificationCaptor.capture());
        Notification saved = notificationCaptor.getValue();
        
        assertEquals("Title", saved.getTitle());
        assertEquals("Body Msg", saved.getBody());
        assertEquals("EXAM", saved.getRelatedEntityType());
        assertEquals(entityId, saved.getRelatedEntityId());
        assertFalse(saved.getIsRead());
        assertEquals(targetUserId, saved.getUser().getUserId());
    }

    // =========================================================================
    // cleanupOldNotifications()
    // =========================================================================

    @Test
    @DisplayName("[N] cleanupOldNotifications - Gọi service xóa cronjob -> Trả về lượng xóa")
    void cleanupOldNotifications_CallsRepository_ReturnsDeletedCount() {
        // Arrange
        when(notificationRepository.deleteReadNotificationsOlderThan(any(OffsetDateTime.class))).thenReturn(200);

        // Act
        int result = notificationService.cleanupOldNotifications();

        // Assert
        assertEquals(200, result);
        verify(notificationRepository).deleteReadNotificationsOlderThan(any(OffsetDateTime.class));
    }
}
