package agsfjope.backend.presentation.controllers;

import agsfjope.backend.application.dtos.responses.notification.NotificationResponse;
import agsfjope.backend.application.dtos.responses.notification.UnreadCountResponse;
import agsfjope.backend.application.notificationservices.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST Controller for the In-App Notification Center (NOTI-001, NOTI-003).
 * <p>
 * All endpoints require a valid JWT Bearer token.
 * Each user may only access and modify their own notifications —
 * this invariant is enforced in {@link agsfjope.backend.application.notificationservices.impl.NotificationServiceImpl}.
 * </p>
 *
 * <p>MSG codes referenced:
 * <ul>
 *   <li>MSG-80 — Đánh dấu thông báo đã đọc thành công</li>
 *   <li>MSG-81 — Đánh dấu tất cả thông báo đã đọc thành công</li>
 *   <li>MSG-82 — Thông báo không tìm thấy (404)</li>
 * </ul>
 * </p>
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {

    private final NotificationService notificationService;

    // ─── GET /api/notifications ────────────────────────────────────────────────

    /**
     * NOTI-003: Returns the list of notifications for the authenticated user.
     * <p>
     * Query param {@code filter} supports:
     * <ul>
     *   <li>"all" (default) — all notifications</li>
     *   <li>"unread"        — only unread notifications</li>
     *   <li>"read"          — only read notifications</li>
     * </ul>
     *
     * @param filter the filter tab selected in the Notification Center UI
     * @return 200 with the notification list
     */
    @GetMapping
    @Operation(summary = "Lấy danh sách thông báo", description = "Filter: all | unread | read")
    public ResponseEntity<Map<String, Object>> getNotifications(
            @RequestParam(defaultValue = "all") String filter) {

        List<NotificationResponse> notifications = notificationService.getMyNotifications(filter);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Lấy danh sách thông báo thành công",
                "data", notifications,
                "errors", ""
        ));
    }

    // ─── GET /api/notifications/unread-count ──────────────────────────────────

    /**
     * NOTI-003: Returns the unread notification count for the badge icon.
     * The frontend polls this endpoint to keep the bell badge up to date.
     *
     * @return 200 with { unreadCount: N }
     */
    @GetMapping("/unread-count")
    @Operation(summary = "Lấy số thông báo chưa đọc (badge count)")
    public ResponseEntity<Map<String, Object>> getUnreadCount() {
        UnreadCountResponse count = notificationService.getUnreadCount();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Lấy số thông báo chưa đọc thành công",
                "data", count,
                "errors", ""
        ));
    }

    // ─── PUT /api/notifications/{id}/read ─────────────────────────────────────

    /**
     * NOTI-003: Marks a specific notification as read.
     * Returns 404 if the notification does not exist or belongs to another user.
     *
     * @param id the notification UUID (MSG-80 on success, MSG-82 on 404)
     * @return 200 on success
     */
    @PutMapping("/{id}/read")
    @Operation(summary = "Đánh dấu 1 thông báo là đã đọc")
    public ResponseEntity<Map<String, Object>> markAsRead(@PathVariable UUID id) {
        notificationService.markAsRead(id);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Đánh dấu thông báo đã đọc thành công",
                "data", "",
                "errors", ""
        ));
    }

    // ─── PUT /api/notifications/read-all ──────────────────────────────────────

    /**
     * NOTI-003: Marks ALL unread notifications of the current user as read.
     * Corresponds to the "Mark all as read" button in the Notification Center UI. (MSG-81)
     *
     * @return 200 on success
     */
    @PutMapping("/read-all")
    @Operation(summary = "Đánh dấu tất cả thông báo là đã đọc")
    public ResponseEntity<Map<String, Object>> markAllAsRead() {
        notificationService.markAllAsRead();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Đã đánh dấu tất cả thông báo là đã đọc",
                "data", "",
                "errors", ""
        ));
    }
}
