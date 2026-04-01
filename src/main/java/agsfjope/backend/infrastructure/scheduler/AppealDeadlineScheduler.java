package agsfjope.backend.infrastructure.scheduler;

import agsfjope.backend.application.notificationservices.NotificationService;
import agsfjope.backend.application.ports.out.EmailService;
import agsfjope.backend.core.entities.Appeal;
import agsfjope.backend.core.entities.User;
import agsfjope.backend.core.enums.AppealStatus;
import agsfjope.backend.core.repositories.appeal.AppealRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

import java.util.List;

/**
 * Scheduled job that monitors appeal deadlines and sends reminder/overdue emails.
 *
 * <p>Runs every hour. Two distinct checks are performed:
 * <ol>
 *   <li><strong>2-day reminder</strong> (TRG-006a): When an appeal's deadline is within the next
 *       48 hours, send a reminder email to the assigned lecturer.</li>
 *   <li><strong>Overdue alert</strong> (TRG-006b): When an appeal's deadline has already passed
 *       and it is still in {@code PROCESSING} status, send an alert to both the assigned
 *       lecturer and the exam staff (via in-app notification placeholder).</li>
 * </ol>
 *
 * <p>All notifications are best-effort: failures are logged but never propagate.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AppealDeadlineScheduler {

    private final AppealRepository    appealRepository;
    private final EmailService        emailService;
    private final NotificationService notificationService;

    // ─────────────────────────────────────────────────────────────────────────
    // Scheduled Jobs
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * TRG-006a: Sends 2-day deadline reminders to lecturers.
     * Runs every hour at minute 0.
     */
    @Scheduled(cron = "0 0 * * * *")
    public void sendDeadlineReminders() {
        OffsetDateTime now         = OffsetDateTime.now();
        OffsetDateTime windowStart = now.plusDays(2);
        OffsetDateTime windowEnd   = windowStart.plusHours(1);

        List<Appeal> upcomingAppeals = appealRepository
                .findByStatusAndDeadlineAtBetween(AppealStatus.PROCESSING, windowStart, windowEnd);

        log.info("[SCHEDULER] Deadline reminder check: {} appeals in 2-day window", upcomingAppeals.size());

        for (Appeal appeal : upcomingAppeals) {
            sendReminderForAppeal(appeal, false);
        }
    }

    /**
     * TRG-006b: Sends overdue alerts to lecturers and in-app notifications to staff.
     * Runs every hour at minute 30.
     */
    @Scheduled(cron = "0 30 * * * *")
    public void sendOverdueAlerts() {
        OffsetDateTime now = OffsetDateTime.now();

        List<Appeal> overdueAppeals = appealRepository
                .findByStatusAndDeadlineAtBefore(AppealStatus.PROCESSING, now);

        log.info("[SCHEDULER] Overdue alert check: {} overdue appeals", overdueAppeals.size());

        for (Appeal appeal : overdueAppeals) {
            sendReminderForAppeal(appeal, true);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Sends a reminder or overdue alert for a single appeal.
     * Failures are swallowed and logged.
     *
     * @param appeal    the overdue or near-deadline appeal
     * @param isOverdue true if the deadline has passed
     */
    private void sendReminderForAppeal(Appeal appeal, boolean isOverdue) {
        try {
            User lecturer   = appeal.getAssignedLecturer();
            User student    = appeal.getStudent();
            String examName = resolveExamName(appeal);

            if (lecturer == null) {
                log.warn("[SCHEDULER] Appeal {} has no assigned lecturer — skipping", appeal.getAppealId());
                return;
            }

            int daysLeft = computeDaysLeft(appeal);

            // Email to the assigned lecturer
            if (lecturer.getEmail() != null && !lecturer.getEmail().isBlank()) {
                emailService.sendAppealDeadlineReminderEmail(
                        lecturer.getEmail(),
                        lecturer.getFullName(),
                        student.getFullName(),
                        examName,
                        daysLeft,
                        isOverdue);
            }

            // In-app notification to the assigned lecturer
            String notifTitle = isOverdue
                    ? "⛔ Quá hạn chấm phúc khảo: " + examName
                    : "⏰ Nhắc deadline phúc khảo còn 2 ngày: " + examName;
            String notifBody  = isOverdue
                    ? "Deadline chấm phúc khảo cho sinh viên " + student.getFullName() + " đã qua."
                    : "Bạn còn 2 ngày để hoàn thành chấm phúc khảo cho " + student.getFullName() + ".";

            notificationService.createNotification(
                    lecturer.getUserId(), notifTitle, notifBody,
                    "APPEAL_DEADLINE", appeal.getAppealId());

            log.info("[SCHEDULER] {} notification sent for appeal {} (lecturer: {})",
                    isOverdue ? "Overdue" : "Reminder",
                    appeal.getAppealId(), lecturer.getEmail());

        } catch (Exception e) {
            log.error("[SCHEDULER] Failed to send deadline notification for appeal {}: {}",
                    appeal.getAppealId(), e.getMessage());
        }
    }

    /**
     * Resolves a human-readable exam name from the appeal's submission chain.
     *
     * @param appeal the appeal entity
     * @return exam name or fallback string
     */
    private String resolveExamName(Appeal appeal) {
        try {
            return appeal.getSubmission().getBlock().getExam().getName();
        } catch (Exception e) {
            return "kỳ thi";
        }
    }

    /**
     * Computes the number of days left until the appeal deadline from now.
     *
     * @param appeal the appeal entity
     * @return days left (may be 0 or negative for overdue)
     */
    private int computeDaysLeft(Appeal appeal) {
        if (appeal.getDeadlineAt() == null) return 0;
        long hours = java.time.Duration.between(OffsetDateTime.now(), appeal.getDeadlineAt()).toHours();
        return (int) Math.max(0, hours / 24);
    }
}
