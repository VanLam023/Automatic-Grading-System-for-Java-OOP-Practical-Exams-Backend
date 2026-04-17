package agsfjope.backend.application.notificationservices.impl;

import agsfjope.backend.application.dtos.responses.notification.NotificationResponse;
import agsfjope.backend.application.dtos.responses.notification.UnreadCountResponse;
import agsfjope.backend.application.notificationservices.NotificationRealtimeService;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

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

    @Mock
    private NotificationRealtimeService notificationRealtimeService;

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
    @DisplayName("[N] getMyNotifications - filter 'unread' -> Trả về Page<NotificationResponse> chỉ chứa unread")
    void getMyNotifications_FilterUnread_ReturnsUnreadPage() {
        // Arrange
        Notification notif = Notification.builder()
                .notificationId(UUID.randomUUID())
                .isRead(false)
                .title("Unread Title")
                .build();
        Pageable pageable = PageRequest.of(0, 10);
        Page<Notification> notifPage = new PageImpl<>(List.of(notif), pageable, 1);

        when(notificationRepository.findByUser_UserIdAndIsReadOrderByCreatedAtDesc(currentUserId, false, pageable))
                .thenReturn(notifPage);

        // Act
        Page<NotificationResponse> result = notificationService.getMyNotifications("unread", 0, 10);

        // Assert
        assertEquals(1, result.getTotalElements());
        assertEquals("Unread Title", result.getContent().get(0).getTitle());
        assertFalse(result.getContent().get(0).getIsRead());
        verify(notificationRepository).findByUser_UserIdAndIsReadOrderByCreatedAtDesc(currentUserId, false, pageable);
    }

    @Test
    @DisplayName("[N] getMyNotifications - filter 'read' -> Trả về Page<NotificationResponse> chỉ chứa read")
    void getMyNotifications_FilterRead_ReturnsReadPage() {
        // Arrange
        Notification notif = Notification.builder()
                .notificationId(UUID.randomUUID())
                .isRead(true)
                .title("Read Title")
                .build();
        Pageable pageable = PageRequest.of(0, 10);
        Page<Notification> notifPage = new PageImpl<>(List.of(notif), pageable, 1);

        when(notificationRepository.findByUser_UserIdAndIsReadOrderByCreatedAtDesc(currentUserId, true, pageable))
                .thenReturn(notifPage);

        // Act
        Page<NotificationResponse> result = notificationService.getMyNotifications("read", 0, 10);

        // Assert
        assertEquals(1, result.getTotalElements());
        assertEquals("Read Title", result.getContent().get(0).getTitle());
        assertTrue(result.getContent().get(0).getIsRead());
        verify(notificationRepository).findByUser_UserIdAndIsReadOrderByCreatedAtDesc(currentUserId, true, pageable);
    }

    @Test
    @DisplayName("[N] getMyNotifications - filter 'all' -> Trả về tất cả Page<NotificationResponse>")
    void getMyNotifications_FilterAll_ReturnsAllPage() {
        // Arrange
        Notification notif1 = Notification.builder().notificationId(UUID.randomUUID()).build();
        Notification notif2 = Notification.builder().notificationId(UUID.randomUUID()).build();
        Pageable pageable = PageRequest.of(0, 10);
        Page<Notification> notifPage = new PageImpl<>(List.of(notif1, notif2), pageable, 2);

        when(notificationRepository.findByUser_UserIdOrderByCreatedAtDesc(currentUserId, pageable))
                .thenReturn(notifPage);

        // Act
        Page<NotificationResponse> result = notificationService.getMyNotifications("all", 0, 10);

        // Assert
        assertEquals(2, result.getTotalElements());
        verify(notificationRepository).findByUser_UserIdOrderByCreatedAtDesc(currentUserId, pageable);
    }

    @Test
    @DisplayName("[B] getMyNotifications - Database rỗng -> Trả về Page rỗng")
    void getMyNotifications_EmptyDatabase_ReturnsEmptyPage() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        Page<Notification> emptyPage = Page.empty(pageable);

        when(notificationRepository.findByUser_UserIdOrderByCreatedAtDesc(currentUserId, pageable))
                .thenReturn(emptyPage);

        // Act
        Page<NotificationResponse> result = notificationService.getMyNotifications("any_other", 0, 10);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("[B] getMyNotifications - page âm -> Chuẩn hoá về 0")
    void getMyNotifications_NegativePage_NormalizesToZero() {
        // Arrange – page=-1 should be treated as 0 internally
        Pageable expectedPageable = PageRequest.of(0, 10);
        Page<Notification> emptyPage = Page.empty(expectedPageable);

        when(notificationRepository.findByUser_UserIdOrderByCreatedAtDesc(currentUserId, expectedPageable))
                .thenReturn(emptyPage);

        // Act
        Page<NotificationResponse> result = notificationService.getMyNotifications("all", -1, 10);

        // Assert
        assertNotNull(result);
        verify(notificationRepository).findByUser_UserIdOrderByCreatedAtDesc(currentUserId, expectedPageable);
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
        verify(notificationRealtimeService).notifyChanged(currentUserId);
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
        verify(notificationRealtimeService, never()).notifyChanged(any());
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
        verify(notificationRealtimeService).notifyChanged(currentUserId);
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
        verify(notificationRealtimeService, never()).notifyChanged(any());
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
        verify(notificationRealtimeService).notifyChanged(targetUserId);
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
