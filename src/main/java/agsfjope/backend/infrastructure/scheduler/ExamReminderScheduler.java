package agsfjope.backend.infrastructure.scheduler;

import agsfjope.backend.application.notificationservices.NotificationService;
import agsfjope.backend.core.entities.Block;
import agsfjope.backend.core.entities.User;
import agsfjope.backend.core.repositories.auth.UserRepository;
import agsfjope.backend.core.repositories.block.BlockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Scheduled job sends reminders before an exam starts.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExamReminderScheduler {

    private final BlockRepository blockRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    /**
     * Gửi thông báo cho tất cả sinh viên trước khi kỳ thi bắt đầu 30 phút.
     * Chạy mỗi 5 phút một lần để check.
     * Window: (now + 25m, now + 30m] -> Những block sẽ start trong 30 phút tới.
     */
    @Scheduled(cron = "0 0/5 * * * *")
    public void sendExamStartReminders() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime windowStart = now.plusMinutes(25);
        OffsetDateTime windowEnd = now.plusMinutes(30);

        List<Block> upcomingBlocks = blockRepository.findByStartTimeBetween(windowStart, windowEnd);

        if (upcomingBlocks.isEmpty()) {
            return;
        }

        log.info("[SCHEDULER] Exam Start Reminder check: found {} blocks starting in ~30 mins", upcomingBlocks.size());

        // Do chưa có bảng Map Sinh Viên - Kỳ Thi, thông báo sẽ gửi tới toàn bộ sinh viên đang hoạt động
        List<User> students = userRepository.findByRole_NameAndDeletedAtIsNull("STUDENT");

        for (Block block : upcomingBlocks) {
            String examName = block.getExam() != null ? block.getExam().getName() : "Không xác định";
            String blockName = block.getName();
            
            String title = "⏰ Kỳ thi sắp bắt đầu - " + examName;
            String body = String.format("Bạn có một ca thi sắp bắt đầu trong 30 phút nữa: Môn %s (Ca: %s). Vui lòng chuẩn bị!", 
                    examName, blockName);

            for (User student : students) {
                try {
                    notificationService.createNotification(
                            student.getUserId(), 
                            title, 
                            body, 
                            "EXAM", 
                            block.getExam() != null ? block.getExam().getExamId() : null);
                } catch (Exception e) {
                    log.error("[SCHEDULER] Failed to send Exam Reminder to student {}: {}", student.getUserId(), e.getMessage());
                }
            }
        }
    }
}
