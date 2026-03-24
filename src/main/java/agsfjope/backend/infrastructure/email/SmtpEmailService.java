package agsfjope.backend.infrastructure.email;

import agsfjope.backend.application.ports.out.EmailService;
import agsfjope.backend.core.repositories.config.SystemConfigRepository;
import agsfjope.backend.infrastructure.security.AesEncryptionUtil;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;
import java.util.stream.Collectors;

import agsfjope.backend.core.entities.SystemConfig;

/**
 * SMTP implementation of {@link EmailService}.
 * <p>
 * Unlike the previous version that used a fixed Spring {@code JavaMailSender} bean,
 * this class reads SMTP credentials dynamically from the {@code SystemConfigs} table
 * on every send operation. This satisfies BR-51: admin changes take effect immediately
 * without a server restart.
 * </p>
 * <p>
 * All public send methods are annotated with {@code @Async} so they execute on the
 * grading/notification thread pool and never block the calling business logic.
 * SMTP failures are logged as ERROR but not re-thrown, to avoid disrupting the
 * main pipeline.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SmtpEmailService implements EmailService {

    // Keys stored in SystemConfigs table
    private static final String KEY_HOST      = "SMTP_HOST";
    private static final String KEY_PORT      = "SMTP_PORT";
    private static final String KEY_USERNAME  = "SMTP_USERNAME";
    private static final String KEY_PASSWORD  = "SMTP_PASSWORD";
    private static final String KEY_FROM      = "SMTP_FROM_EMAIL";

    private static final List<String> SMTP_KEYS =
            List.of(KEY_HOST, KEY_PORT, KEY_USERNAME, KEY_PASSWORD, KEY_FROM);

    private final SystemConfigRepository systemConfigRepository;
    private final AesEncryptionUtil aesEncryptionUtil;

    // ─────────────────────────────────────────────────────────────────────────
    // Auth Emails (giữ nguyên 3 method cũ)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Sends a branded HTML password-reset email to the given address.
     *
     * @param to        recipient's email address
     * @param resetLink full reset-password URL
     */
    @Async
    @Override
    public void sendPasswordResetEmail(String to, String resetLink) {
        String subject = "[OOP Exam] Đặt lại mật khẩu của bạn";
        String html    = buildResetPasswordEmailTemplate(resetLink);
        logAndSend(to, subject, html, "password-reset");
    }

    /**
     * Sends a branded HTML account-activation email to the newly registered student.
     *
     * @param to             recipient's FPT email address
     * @param activationLink full verify-account URL
     */
    @Async
    @Override
    public void sendActivationEmail(String to, String activationLink) {
        String subject = "[OOP Exam] Kích hoạt tài khoản của bạn";
        String html    = buildActivationEmailTemplate(activationLink);
        logAndSend(to, subject, html, "activation");
    }

    /**
     * Sends account credentials email to a student created via Admin Excel import.
     *
     * @param to             recipient's FPT email address
     * @param username       the auto-generated username
     * @param password       the plain-text default password shown once in the email
     * @param activationLink the verify-account URL to embed
     */
    @Async
    @Override
    public void sendAccountCredentialsEmail(String to, String username,
                                            String password, String activationLink) {
        String subject = "[OOP Exam] Tài khoản của bạn đã được tạo";
        String html    = buildAccountCredentialsEmailTemplate(username, password, activationLink);
        logAndSend(to, subject, html, "account-credentials");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Notification Emails — TRG-001~006
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * TRG-001: Notifies a student that their submission has been graded.
     *
     * @param to          student's email address
     * @param studentName student's full name
     * @param examName    name of the exam
     * @param blockName   block name (e.g. "Block 10")
     * @param totalScore  final score
     * @param isPassed    true if totalScore >= 4.0
     */
    @Async
    @Override
    public void sendGradingCompleteEmail(String to, String studentName, String examName,
                                         String blockName, double totalScore, boolean isPassed) {
        String subject = "[OOP Exam] Kết quả chấm bài - " + examName;
        String html    = buildGradingCompleteTemplate(studentName, examName, blockName, totalScore, isPassed);
        logAndSend(to, subject, html, "grading-complete");
    }

    /**
     * TRG-003: Confirms to a student that their appeal payment was received.
     *
     * @param to            student's email address
     * @param studentName   student's full name
     * @param examName      name of the exam
     * @param amountVnd     paid amount in VND
     * @param transactionId PayOS transaction reference
     */
    @Async
    @Override
    public void sendPaymentSuccessEmail(String to, String studentName, String examName,
                                        long amountVnd, String transactionId) {
        String subject = "[OOP Exam] Thanh toán phúc khảo thành công - " + examName;
        String html    = buildPaymentSuccessTemplate(studentName, examName, amountVnd, transactionId);
        logAndSend(to, subject, html, "payment-success");
    }

    /**
     * TRG-002: Notifies a student that their appeal was approved and score updated.
     *
     * @param to          student's email address
     * @param studentName student's full name
     * @param examName    name of the exam
     * @param newScore    updated final score
     */
    @Async
    @Override
    public void sendAppealApprovedEmail(String to, String studentName,
                                        String examName, double newScore) {
        String subject = "[OOP Exam] Phúc khảo được chấp thuận - " + examName;
        String html    = buildAppealApprovedTemplate(studentName, examName, newScore);
        logAndSend(to, subject, html, "appeal-approved");
    }

    /**
     * TRG-002: Notifies a student that their appeal was denied.
     *
     * @param to              student's email address
     * @param studentName     student's full name
     * @param examName        name of the exam
     * @param lecturerComment reviewer's comment
     */
    @Async
    @Override
    public void sendAppealDeniedEmail(String to, String studentName,
                                      String examName, String lecturerComment) {
        String subject = "[OOP Exam] Kết quả phúc khảo - " + examName;
        String html    = buildAppealDeniedTemplate(studentName, examName, lecturerComment);
        logAndSend(to, subject, html, "appeal-denied");
    }

    /**
     * TRG-005: Notifies a lecturer that an appeal has been assigned to them.
     *
     * @param to           lecturer's email address
     * @param lecturerName lecturer's full name
     * @param studentName  student's full name
     * @param examName     name of the exam
     * @param deadline     formatted deadline string
     */
    @Async
    @Override
    public void sendAppealAssignedEmail(String to, String lecturerName, String studentName,
                                        String examName, String deadline) {
        String subject = "[OOP Exam] Bạn được phân công chấm phúc khảo - " + examName;
        String html    = buildAppealAssignedTemplate(lecturerName, studentName, examName, deadline);
        logAndSend(to, subject, html, "appeal-assigned");
    }

    /**
     * TRG-006: Sends a deadline reminder or overdue alert for an appeal.
     *
     * @param to            recipient's email address
     * @param recipientName recipient's full name
     * @param studentName   student's full name
     * @param examName      name of the exam
     * @param daysLeft      days left before deadline (ignored when isOverdue=true)
     * @param isOverdue     true if the deadline has already passed
     */
    @Async
    @Override
    public void sendAppealDeadlineReminderEmail(String to, String recipientName, String studentName,
                                                 String examName, int daysLeft, boolean isOverdue) {
        String subject = isOverdue
                ? "[OOP Exam] ⚠️ Quá hạn chấm phúc khảo - " + examName
                : "[OOP Exam] ⏰ Nhắc nhở deadline phúc khảo - " + examName;
        String html = buildDeadlineReminderTemplate(recipientName, studentName, examName, daysLeft, isOverdue);
        logAndSend(to, subject, html, isOverdue ? "appeal-overdue" : "appeal-reminder");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Core: Dynamic SMTP Sender Builder
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Builds a fresh {@link JavaMailSenderImpl} by reading SMTP configuration
     * from the {@code SystemConfigs} database table.
     * A new instance is created on each call so that admin config changes
     * take effect immediately without a server restart (BR-51).
     *
     * @return configured mail sender ready to send
     */
    private JavaMailSenderImpl buildMailSender() {
        // Load all 5 required SMTP keys from DB in one query
        Map<String, SystemConfig> configMap = systemConfigRepository
                .findByConfigKeyIn(SMTP_KEYS)
                .stream()
                .collect(Collectors.toMap(SystemConfig::getConfigKey, Function.identity()));

        // Decrypt AES-encrypted fields (USERNAME, PASSWORD)
        String host     = configMap.get(KEY_HOST).getConfigValue();
        int    port     = Integer.parseInt(configMap.get(KEY_PORT).getConfigValue());
        String username = decryptIfNeeded(configMap.get(KEY_USERNAME));
        String password = decryptIfNeeded(configMap.get(KEY_PASSWORD));
        String fromEmail = configMap.get(KEY_FROM).getConfigValue();

        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        sender.setUsername(username);
        sender.setPassword(password);
        sender.setDefaultEncoding("UTF-8");

        // Store fromEmail for use in send method
        sender.getJavaMailProperties().put("mail.smtp.from", fromEmail);

        // Standard SMTP TLS properties
        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth",                 "true");
        props.put("mail.smtp.starttls.enable",      "true");
        props.put("mail.smtp.connectiontimeout",    "10000");
        props.put("mail.smtp.timeout",              "10000");
        props.put("mail.smtp.writetimeout",         "10000");

        return sender;
    }

    /**
     * Decrypts the config value if {@code isEncrypted} flag is true.
     *
     * @param config the SystemConfig entity to read
     * @return plain-text value
     */
    private String decryptIfNeeded(SystemConfig config) {
        if (Boolean.TRUE.equals(config.getIsEncrypted())) {
            return aesEncryptionUtil.decrypt(config.getConfigValue());
        }
        return config.getConfigValue();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Core: Log & Send Helper
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Logs the outgoing email and sends it via a dynamically-built SMTP sender.
     * SMTP failures are caught and logged as ERROR — never re-thrown — so that
     * notification failures do not disrupt the calling business transaction.
     *
     * @param to        recipient email address
     * @param subject   email subject line
     * @param html      full HTML body
     * @param emailType short label used in log messages (e.g. "grading-complete")
     */
    private void logAndSend(String to, String subject, String html, String emailType) {
        log.info("[EMAIL] Sending {} email to: {}", emailType, to);
        log.debug("[EMAIL] Subject: {} | HTML length: {} chars", subject, html.length());

        try {
            JavaMailSenderImpl sender = buildMailSender();
            String fromEmail = sender.getJavaMailProperties()
                                     .getProperty("mail.smtp.from", sender.getUsername());

            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true); // true = isHtml

            sender.send(message);
            log.info("[EMAIL] {} email sent successfully to {}", emailType, to);

        } catch (MessagingException e) {
            log.error("[EMAIL] Failed to send {} email to {}: {}", emailType, to, e.getMessage());
        } catch (Exception e) {
            // Catch config lookup errors (e.g. missing SMTP keys in DB)
            log.error("[EMAIL] Could not build SMTP sender for {} email to {}: {}",
                      emailType, to, e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HTML Template Builders — Auth (3 cũ)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Builds a branded orange HTML template for the password-reset email.
     *
     * @param resetLink the URL to embed in the CTA button
     * @return HTML string ready to be sent as email body
     */
    private String buildResetPasswordEmailTemplate(String resetLink) {
        return """
            <!DOCTYPE html>
            <html lang="vi">
            <head><meta charset="UTF-8"/>
              <meta name="viewport" content="width=device-width,initial-scale=1.0"/>
              <title>Đặt lại mật khẩu</title>
            </head>
            <body style="margin:0;padding:0;background:#f4f4f4;font-family:Arial,Helvetica,sans-serif;">
              <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f4f4;padding:40px 0;">
                <tr><td align="center">
                  <table width="560" cellpadding="0" cellspacing="0"
                         style="background:#ffffff;border-radius:8px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,.12);">
                    <!-- HEADER -->
                    <tr><td style="background:#f37120;padding:32px 40px;text-align:center;">
                      <h1 style="margin:0;color:#fff;font-size:24px;letter-spacing:1px;">OOP Exam System</h1>
                      <p style="margin:8px 0 0;color:#ffe0c4;font-size:14px;">Hệ thống Chấm điểm Bài thi Lập trình</p>
                    </td></tr>
                    <!-- BODY -->
                    <tr><td style="padding:40px 40px 24px;">
                      <h2 style="margin:0 0 16px;color:#1a1a1a;font-size:20px;">Đặt lại mật khẩu của bạn</h2>
                      <p style="margin:0 0 16px;color:#555;font-size:15px;line-height:1.7;">
                        Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản của bạn.<br/>
                        Nhấn vào nút bên dưới để tiến hành tạo mật khẩu mới:
                      </p>
                      <table cellpadding="0" cellspacing="0" style="margin:24px 0;">
                        <tr><td style="background:#f37120;border-radius:6px;">
                          <a href="%s" style="display:inline-block;padding:14px 36px;color:#fff;font-size:16px;font-weight:bold;text-decoration:none;border-radius:6px;">
                            &#128274;&nbsp;&nbsp;Đặt lại mật khẩu
                          </a>
                        </td></tr>
                      </table>
                      <p style="margin:0 0 8px;color:#888;font-size:13px;">⏰ Link sẽ <strong>hết hạn sau 15 phút</strong>.</p>
                      <p style="margin:0;color:#888;font-size:13px;">Nếu bạn không yêu cầu đặt lại mật khẩu, vui lòng bỏ qua email này.</p>
                    </td></tr>
                    <tr><td style="padding:0 40px;"><hr style="border:none;border-top:1px solid #eee;margin:0;"/></td></tr>
                    <tr><td style="padding:16px 40px 32px;">
                      <p style="margin:0;color:#aaa;font-size:12px;line-height:1.6;">
                        Nếu nút không hoạt động, copy URL sau vào trình duyệt:<br/>
                        <a href="%s" style="color:#f37120;word-break:break-all;">%s</a>
                      </p>
                    </td></tr>
                    <!-- FOOTER -->
                    <tr><td style="background:#f9f9f9;padding:16px 40px;text-align:center;">
                      <p style="margin:0;color:#bbb;font-size:12px;">© 2026 OOP Exam System &middot; AGSFJOPE Team</p>
                    </td></tr>
                  </table>
                </td></tr>
              </table>
            </body>
            </html>
            """.formatted(resetLink, resetLink, resetLink);
    }

    /**
     * Builds a branded green HTML template for the account-activation email.
     *
     * @param activationLink the URL containing the activation JWT token
     * @return HTML string ready to be sent as email body
     */
    private String buildActivationEmailTemplate(String activationLink) {
        return """
            <!DOCTYPE html>
            <html lang="vi">
            <head><meta charset="UTF-8"/>
              <meta name="viewport" content="width=device-width,initial-scale=1.0"/>
              <title>Kích hoạt tài khoản</title>
            </head>
            <body style="margin:0;padding:0;background:#f4f4f4;font-family:Arial,Helvetica,sans-serif;">
              <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f4f4;padding:40px 0;">
                <tr><td align="center">
                  <table width="560" cellpadding="0" cellspacing="0"
                         style="background:#fff;border-radius:8px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,.12);">
                    <tr><td style="background:#f37120;padding:32px 40px;text-align:center;">
                      <h1 style="margin:0;color:#fff;font-size:24px;letter-spacing:1px;">OOP Exam System</h1>
                      <p style="margin:8px 0 0;color:#ffe0c4;font-size:14px;">Hệ thống Chấm điểm Bài thi Lập trình</p>
                    </td></tr>
                    <tr><td style="padding:40px 40px 24px;">
                      <h2 style="margin:0 0 16px;color:#1a1a1a;font-size:20px;">Chào mừng bạn đến với OOP Exam! 🎉</h2>
                      <p style="margin:0 0 16px;color:#555;font-size:15px;line-height:1.7;">
                        Tài khoản của bạn đã được tạo thành công.<br/>
                        Nhấn vào nút bên dưới để <strong>kích hoạt tài khoản</strong> và bắt đầu sử dụng:
                      </p>
                      <table cellpadding="0" cellspacing="0" style="margin:24px 0;">
                        <tr><td style="background:#16a34a;border-radius:6px;">
                          <a href="%s" style="display:inline-block;padding:14px 36px;color:#fff;font-size:16px;font-weight:bold;text-decoration:none;border-radius:6px;">
                            &#9989;&nbsp;&nbsp;Kích hoạt tài khoản
                          </a>
                        </td></tr>
                      </table>
                      <p style="margin:0 0 8px;color:#888;font-size:13px;">⏰ Link sẽ <strong>hết hạn sau 24 giờ</strong>.</p>
                      <p style="margin:0;color:#888;font-size:13px;">Nếu bạn không thực hiện đăng ký, bỏ qua email này.</p>
                    </td></tr>
                    <tr><td style="padding:0 40px;"><hr style="border:none;border-top:1px solid #eee;margin:0;"/></td></tr>
                    <tr><td style="padding:16px 40px 32px;">
                      <p style="margin:0;color:#aaa;font-size:12px;line-height:1.6;">
                        Nếu nút không hoạt động:<br/>
                        <a href="%s" style="color:#16a34a;word-break:break-all;">%s</a>
                      </p>
                    </td></tr>
                    <tr><td style="background:#f9f9f9;padding:16px 40px;text-align:center;">
                      <p style="margin:0;color:#bbb;font-size:12px;">© 2026 OOP Exam System &middot; AGSFJOPE Team</p>
                    </td></tr>
                  </table>
                </td></tr>
              </table>
            </body>
            </html>
            """.formatted(activationLink, activationLink, activationLink);
    }

    /**
     * Builds a branded blue HTML template for the account-credentials email.
     *
     * @param username  the auto-generated username
     * @param password  the plain-text default password displayed once
     * @param resetLink the reset-password URL
     * @return HTML string ready to be sent as email body
     */
    private String buildAccountCredentialsEmailTemplate(String username,
                                                         String password,
                                                         String resetLink) {
        return """
            <!DOCTYPE html>
            <html lang="vi">
            <head><meta charset="UTF-8"/>
              <meta name="viewport" content="width=device-width,initial-scale=1.0"/>
              <title>Thông tin tài khoản OOP Exam</title>
            </head>
            <body style="margin:0;padding:0;background:#f4f4f4;font-family:Arial,Helvetica,sans-serif;">
              <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f4f4;padding:40px 0;">
                <tr><td align="center">
                  <table width="560" cellpadding="0" cellspacing="0"
                         style="background:#fff;border-radius:8px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,.12);">
                    <tr><td style="background:#f37120;padding:32px 40px;text-align:center;">
                      <h1 style="margin:0;color:#fff;font-size:24px;letter-spacing:1px;">OOP Exam System</h1>
                      <p style="margin:8px 0 0;color:#ffe0c4;font-size:14px;">Hệ thống Chấm điểm Bài thi Lập trình</p>
                    </td></tr>
                    <tr><td style="padding:40px 40px 24px;">
                      <h2 style="margin:0 0 16px;color:#1a1a1a;font-size:20px;">🎓 Tài khoản đã được tạo cho bạn</h2>
                      <p style="margin:0 0 20px;color:#555;font-size:15px;line-height:1.7;">
                        Quản trị viên đã tạo tài khoản OOP Exam cho bạn. Dưới đây là thông tin đăng nhập:
                      </p>
                      <!-- CREDENTIALS BOX -->
                      <table cellpadding="0" cellspacing="0" width="100%%"
                             style="margin:0 0 24px;background:#f0f7ff;border-radius:8px;border:1px solid #bfdbfe;">
                        <tr><td style="padding:20px 24px;">
                          <p style="margin:0 0 10px;font-size:14px;color:#666;">
                            <strong style="color:#1d4ed8;">Tên đăng nhập:</strong>&nbsp;
                            <span style="font-family:monospace;font-size:16px;color:#1a1a1a;font-weight:bold;">%s</span>
                          </p>
                          <p style="margin:0;font-size:14px;color:#666;">
                            <strong style="color:#1d4ed8;">Mật khẩu tạm thời:</strong>&nbsp;
                            <span style="font-family:monospace;font-size:16px;color:#1a1a1a;font-weight:bold;">%s</span>
                          </p>
                        </td></tr>
                      </table>
                      <!-- WARNING -->
                      <table cellpadding="0" cellspacing="0" width="100%%"
                             style="margin:0 0 20px;background:#fff7ed;border-radius:8px;border-left:4px solid #f37120;">
                        <tr><td style="padding:16px 20px;">
                          <p style="margin:0;font-size:14px;color:#92400e;line-height:1.6;">
                            ⚠️ <strong>Quan trọng:</strong> Tài khoản chưa được kích hoạt.<br/>
                            Vui lòng nhấn nút bên dưới để <strong>đặt lại mật khẩu</strong> và kích hoạt tài khoản.
                          </p>
                        </td></tr>
                      </table>
                      <table cellpadding="0" cellspacing="0" style="margin:0 0 16px;">
                        <tr><td style="border-radius:6px;background:#f37120;">
                          <a href="%s" style="display:inline-block;padding:12px 28px;font-size:15px;font-weight:bold;color:#fff;text-decoration:none;border-radius:6px;">
                            🔐 Đặt lại mật khẩu &amp; Kích hoạt
                          </a>
                        </td></tr>
                      </table>
                      <p style="margin:0 0 20px;color:#aaa;font-size:11px;word-break:break-all;">
                        Hoặc copy link: <a href="%s" style="color:#1d4ed8;">%s</a>&nbsp;(hiệu lực 24 giờ)
                      </p>
                      <p style="margin:0;color:#888;font-size:13px;">
                        Nếu bạn không biết mình đã đăng ký hệ thống này, bỏ qua email này.
                      </p>
                    </td></tr>
                    <tr><td style="padding:0 40px;"><hr style="border:none;border-top:1px solid #eee;margin:0;"/></td></tr>
                    <tr><td style="background:#f9f9f9;padding:16px 40px;text-align:center;">
                      <p style="margin:0;color:#bbb;font-size:12px;">© 2026 OOP Exam System &middot; AGSFJOPE Team</p>
                    </td></tr>
                  </table>
                </td></tr>
              </table>
            </body>
            </html>
            """.formatted(username, password, resetLink, resetLink, resetLink);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HTML Template Builders — Notification (6 mới)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * TRG-001: Blue template for grading complete notification.
     */
    private String buildGradingCompleteTemplate(String studentName, String examName,
                                                 String blockName, double totalScore,
                                                 boolean isPassed) {
        // Determine badge color and label based on pass/fail status
        String badgeBg    = isPassed ? "#16a34a" : "#dc2626";
        String badgeLabel = isPassed ? "✅ ĐẠT" : "❌ KHÔNG ĐẠT";
        String scoreText  = String.format("%.1f / 10.0", totalScore);

        return """
            <!DOCTYPE html>
            <html lang="vi">
            <head><meta charset="UTF-8"/><title>Kết quả chấm bài</title></head>
            <body style="margin:0;padding:0;background:#f4f4f4;font-family:Arial,Helvetica,sans-serif;">
              <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f4f4;padding:40px 0;">
                <tr><td align="center">
                  <table width="560" cellpadding="0" cellspacing="0"
                         style="background:#fff;border-radius:8px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,.12);">
                    <tr><td style="background:#1d4ed8;padding:32px 40px;text-align:center;">
                      <h1 style="margin:0;color:#fff;font-size:24px;letter-spacing:1px;">OOP Exam System</h1>
                      <p style="margin:8px 0 0;color:#bfdbfe;font-size:14px;">Kết quả chấm điểm bài thi</p>
                    </td></tr>
                    <tr><td style="padding:40px;">
                      <h2 style="margin:0 0 8px;color:#1a1a1a;font-size:20px;">📊 Bài thi của bạn đã được chấm</h2>
                      <p style="margin:0 0 24px;color:#555;font-size:15px;">Xin chào <strong>%s</strong>,</p>
                      <!-- RESULT BOX -->
                      <table cellpadding="0" cellspacing="0" width="100%%"
                             style="background:#f8faff;border-radius:8px;border:1px solid #dbeafe;margin-bottom:24px;">
                        <tr><td style="padding:24px;">
                          <p style="margin:0 0 8px;color:#666;font-size:14px;"><strong>Kỳ thi:</strong> %s</p>
                          <p style="margin:0 0 16px;color:#666;font-size:14px;"><strong>Block:</strong> %s</p>
                          <table cellpadding="0" cellspacing="0" width="100%%">
                            <tr>
                              <td style="font-size:14px;color:#666;"><strong>Điểm số:</strong></td>
                              <td align="right">
                                <span style="font-size:28px;font-weight:bold;color:#1d4ed8;">%s</span>
                              </td>
                            </tr>
                            <tr><td colspan="2" style="padding-top:12px;">
                              <span style="display:inline-block;padding:6px 18px;border-radius:20px;background:%s;color:#fff;font-weight:bold;font-size:14px;">
                                %s
                              </span>
                            </td></tr>
                          </table>
                        </td></tr>
                      </table>
                      <p style="margin:0;color:#555;font-size:14px;line-height:1.7;">
                        Đăng nhập vào hệ thống để xem chi tiết kết quả từng câu hỏi và nhận xét AI.
                      </p>
                    </td></tr>
                    <tr><td style="background:#f9f9f9;padding:16px 40px;text-align:center;">
                      <p style="margin:0;color:#bbb;font-size:12px;">© 2026 OOP Exam System &middot; AGSFJOPE Team</p>
                    </td></tr>
                  </table>
                </td></tr>
              </table>
            </body>
            </html>
            """.formatted(studentName, examName, blockName, scoreText, badgeBg, badgeLabel);
    }

    /**
     * TRG-003: Green template for successful appeal payment confirmation.
     */
    private String buildPaymentSuccessTemplate(String studentName, String examName,
                                                long amountVnd, String transactionId) {
        // Format amount with thousands separator
        String formattedAmount = String.format("%,d VND", amountVnd).replace(',', '.');

        return """
            <!DOCTYPE html>
            <html lang="vi">
            <head><meta charset="UTF-8"/><title>Thanh toán phúc khảo thành công</title></head>
            <body style="margin:0;padding:0;background:#f4f4f4;font-family:Arial,Helvetica,sans-serif;">
              <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f4f4;padding:40px 0;">
                <tr><td align="center">
                  <table width="560" cellpadding="0" cellspacing="0"
                         style="background:#fff;border-radius:8px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,.12);">
                    <tr><td style="background:#16a34a;padding:32px 40px;text-align:center;">
                      <h1 style="margin:0;color:#fff;font-size:24px;letter-spacing:1px;">OOP Exam System</h1>
                      <p style="margin:8px 0 0;color:#bbf7d0;font-size:14px;">Xác nhận thanh toán phúc khảo</p>
                    </td></tr>
                    <tr><td style="padding:40px;">
                      <h2 style="margin:0 0 8px;color:#1a1a1a;font-size:20px;">✅ Thanh toán thành công!</h2>
                      <p style="margin:0 0 24px;color:#555;font-size:15px;">Xin chào <strong>%s</strong>,</p>
                      <!-- PAYMENT BOX -->
                      <table cellpadding="0" cellspacing="0" width="100%%"
                             style="background:#f0fdf4;border-radius:8px;border:1px solid #bbf7d0;margin-bottom:24px;">
                        <tr><td style="padding:24px;">
                          <p style="margin:0 0 8px;color:#666;font-size:14px;"><strong>Kỳ thi:</strong> %s</p>
                          <p style="margin:0 0 8px;color:#666;font-size:14px;">
                            <strong>Số tiền:</strong>
                            <span style="font-size:18px;font-weight:bold;color:#16a34a;"> %s</span>
                          </p>
                          <p style="margin:0;color:#666;font-size:14px;">
                            <strong>Mã giao dịch:</strong>
                            <span style="font-family:monospace;color:#1a1a1a;">%s</span>
                          </p>
                        </td></tr>
                      </table>
                      <p style="margin:0;color:#555;font-size:14px;line-height:1.7;">
                        Đơn phúc khảo của bạn đã được gửi thành công. Exam Staff sẽ phân công giảng viên chấm lại.
                        Bạn sẽ nhận được thông báo khi có kết quả.
                      </p>
                    </td></tr>
                    <tr><td style="background:#f9f9f9;padding:16px 40px;text-align:center;">
                      <p style="margin:0;color:#bbb;font-size:12px;">© 2026 OOP Exam System &middot; AGSFJOPE Team</p>
                    </td></tr>
                  </table>
                </td></tr>
              </table>
            </body>
            </html>
            """.formatted(studentName, examName, formattedAmount, transactionId);
    }

    /**
     * TRG-002: Green template for appeal approved notification.
     */
    private String buildAppealApprovedTemplate(String studentName, String examName,
                                                double newScore) {
        String scoreText = String.format("%.1f / 10.0", newScore);
        return """
            <!DOCTYPE html>
            <html lang="vi">
            <head><meta charset="UTF-8"/><title>Phúc khảo được chấp thuận</title></head>
            <body style="margin:0;padding:0;background:#f4f4f4;font-family:Arial,Helvetica,sans-serif;">
              <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f4f4;padding:40px 0;">
                <tr><td align="center">
                  <table width="560" cellpadding="0" cellspacing="0"
                         style="background:#fff;border-radius:8px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,.12);">
                    <tr><td style="background:#16a34a;padding:32px 40px;text-align:center;">
                      <h1 style="margin:0;color:#fff;font-size:24px;letter-spacing:1px;">OOP Exam System</h1>
                      <p style="margin:8px 0 0;color:#bbf7d0;font-size:14px;">Kết quả phúc khảo</p>
                    </td></tr>
                    <tr><td style="padding:40px;">
                      <h2 style="margin:0 0 8px;color:#1a1a1a;font-size:20px;">🎉 Phúc khảo được chấp thuận!</h2>
                      <p style="margin:0 0 24px;color:#555;font-size:15px;">Xin chào <strong>%s</strong>,</p>
                      <table cellpadding="0" cellspacing="0" width="100%%"
                             style="background:#f0fdf4;border-radius:8px;border:1px solid #bbf7d0;margin-bottom:24px;">
                        <tr><td style="padding:24px;">
                          <p style="margin:0 0 8px;color:#666;font-size:14px;"><strong>Kỳ thi:</strong> %s</p>
                          <p style="margin:0;color:#666;font-size:14px;">
                            <strong>Điểm mới:</strong>
                            <span style="font-size:24px;font-weight:bold;color:#16a34a;"> %s</span>
                          </p>
                        </td></tr>
                      </table>
                      <p style="margin:0;color:#555;font-size:14px;line-height:1.7;">
                        Điểm số của bạn đã được cập nhật. Phí phúc khảo sẽ được hoàn trả trong vòng 1–3 ngày làm việc.
                      </p>
                    </td></tr>
                    <tr><td style="background:#f9f9f9;padding:16px 40px;text-align:center;">
                      <p style="margin:0;color:#bbb;font-size:12px;">© 2026 OOP Exam System &middot; AGSFJOPE Team</p>
                    </td></tr>
                  </table>
                </td></tr>
              </table>
            </body>
            </html>
            """.formatted(studentName, examName, scoreText);
    }

    /**
     * TRG-002: Red template for appeal denied notification.
     */
    private String buildAppealDeniedTemplate(String studentName, String examName,
                                              String lecturerComment) {
        // Show comment block only when non-empty
        String commentBlock = (lecturerComment != null && !lecturerComment.isBlank())
                ? """
                  <table cellpadding="0" cellspacing="0" width="100%%"
                         style="margin:16px 0 0;background:#fef2f2;border-radius:8px;border-left:4px solid #dc2626;">
                    <tr><td style="padding:16px 20px;">
                      <p style="margin:0;font-size:14px;color:#7f1d1d;line-height:1.6;">
                        <strong>Nhận xét của giảng viên:</strong><br/>%s
                      </p>
                    </td></tr>
                  </table>
                  """.formatted(lecturerComment)
                : "";

        return """
            <!DOCTYPE html>
            <html lang="vi">
            <head><meta charset="UTF-8"/><title>Kết quả phúc khảo</title></head>
            <body style="margin:0;padding:0;background:#f4f4f4;font-family:Arial,Helvetica,sans-serif;">
              <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f4f4;padding:40px 0;">
                <tr><td align="center">
                  <table width="560" cellpadding="0" cellspacing="0"
                         style="background:#fff;border-radius:8px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,.12);">
                    <tr><td style="background:#dc2626;padding:32px 40px;text-align:center;">
                      <h1 style="margin:0;color:#fff;font-size:24px;letter-spacing:1px;">OOP Exam System</h1>
                      <p style="margin:8px 0 0;color:#fecaca;font-size:14px;">Kết quả phúc khảo</p>
                    </td></tr>
                    <tr><td style="padding:40px;">
                      <h2 style="margin:0 0 8px;color:#1a1a1a;font-size:20px;">Kết quả phúc khảo - Không thay đổi điểm</h2>
                      <p style="margin:0 0 24px;color:#555;font-size:15px;">Xin chào <strong>%s</strong>,</p>
                      <table cellpadding="0" cellspacing="0" width="100%%"
                             style="background:#fef2f2;border-radius:8px;border:1px solid #fecaca;margin-bottom:0;">
                        <tr><td style="padding:24px;">
                          <p style="margin:0 0 8px;color:#666;font-size:14px;"><strong>Kỳ thi:</strong> %s</p>
                          <p style="margin:0;color:#7f1d1d;font-size:14px;">
                            Sau khi xem xét, giảng viên xác nhận rằng điểm số ban đầu là chính xác.
                            Điểm không thay đổi và phí phúc khảo sẽ không được hoàn trả.
                          </p>
                        </td></tr>
                      </table>
                      %s
                    </td></tr>
                    <tr><td style="background:#f9f9f9;padding:16px 40px;text-align:center;">
                      <p style="margin:0;color:#bbb;font-size:12px;">© 2026 OOP Exam System &middot; AGSFJOPE Team</p>
                    </td></tr>
                  </table>
                </td></tr>
              </table>
            </body>
            </html>
            """.formatted(studentName, examName, commentBlock);
    }

    /**
     * TRG-005: Orange template for appeal assignment notification to lecturer.
     */
    private String buildAppealAssignedTemplate(String lecturerName, String studentName,
                                                String examName, String deadline) {
        return """
            <!DOCTYPE html>
            <html lang="vi">
            <head><meta charset="UTF-8"/><title>Phân công chấm phúc khảo</title></head>
            <body style="margin:0;padding:0;background:#f4f4f4;font-family:Arial,Helvetica,sans-serif;">
              <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f4f4;padding:40px 0;">
                <tr><td align="center">
                  <table width="560" cellpadding="0" cellspacing="0"
                         style="background:#fff;border-radius:8px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,.12);">
                    <tr><td style="background:#f37120;padding:32px 40px;text-align:center;">
                      <h1 style="margin:0;color:#fff;font-size:24px;letter-spacing:1px;">OOP Exam System</h1>
                      <p style="margin:8px 0 0;color:#ffe0c4;font-size:14px;">Phân công chấm phúc khảo</p>
                    </td></tr>
                    <tr><td style="padding:40px;">
                      <h2 style="margin:0 0 8px;color:#1a1a1a;font-size:20px;">📋 Bạn được phân công chấm phúc khảo</h2>
                      <p style="margin:0 0 24px;color:#555;font-size:15px;">Xin chào <strong>%s</strong>,</p>
                      <table cellpadding="0" cellspacing="0" width="100%%"
                             style="background:#fff7ed;border-radius:8px;border:1px solid #fed7aa;margin-bottom:24px;">
                        <tr><td style="padding:24px;">
                          <p style="margin:0 0 8px;color:#666;font-size:14px;"><strong>Sinh viên:</strong> %s</p>
                          <p style="margin:0 0 8px;color:#666;font-size:14px;"><strong>Kỳ thi:</strong> %s</p>
                          <p style="margin:0;color:#666;font-size:14px;">
                            <strong>Deadline:</strong>
                            <span style="color:#dc2626;font-weight:bold;"> %s</span>
                          </p>
                        </td></tr>
                      </table>
                      <table cellpadding="0" cellspacing="0" width="100%%"
                             style="background:#fff7ed;border-radius:8px;border-left:4px solid #f37120;margin-bottom:16px;">
                        <tr><td style="padding:16px 20px;">
                          <p style="margin:0;font-size:14px;color:#92400e;line-height:1.6;">
                            ⚠️ <strong>Lưu ý:</strong> Bạn có <strong>7 ngày</strong> để hoàn thành việc chấm lại.
                            Đăng nhập vào hệ thống để xem bài làm và nhập kết quả chấm.
                          </p>
                        </td></tr>
                      </table>
                    </td></tr>
                    <tr><td style="background:#f9f9f9;padding:16px 40px;text-align:center;">
                      <p style="margin:0;color:#bbb;font-size:12px;">© 2026 OOP Exam System &middot; AGSFJOPE Team</p>
                    </td></tr>
                  </table>
                </td></tr>
              </table>
            </body>
            </html>
            """.formatted(lecturerName, studentName, examName, deadline);
    }

    /**
     * TRG-006: Yellow (reminder) or dark-red (overdue) template for deadline alerts.
     */
    private String buildDeadlineReminderTemplate(String recipientName, String studentName,
                                                  String examName, int daysLeft,
                                                  boolean isOverdue) {
        // Choose colors and message based on overdue status
        String headerBg   = isOverdue ? "#7f1d1d" : "#b45309";
        String headerSub  = isOverdue ? "Cảnh báo quá hạn" : "Nhắc nhở deadline";
        String title      = isOverdue
                ? "⛔ Bạn đã quá hạn chấm phúc khảo!"
                : "⏰ Còn " + daysLeft + " ngày để hoàn thành chấm phúc khảo";
        String bodyText   = isOverdue
                ? "Deadline đã qua nhưng bạn chưa nộp kết quả chấm phúc khảo. "
                  + "Vui lòng đăng nhập và hoàn thành ngay. Exam Staff đã được thông báo."
                : "Bạn còn <strong>" + daysLeft + " ngày</strong> để hoàn thành chấm phúc khảo. "
                  + "Vui lòng đăng nhập và hoàn thành trước deadline.";
        String boxBg      = isOverdue ? "#fef2f2" : "#fefce8";
        String boxBorder  = isOverdue ? "#fecaca" : "#fde68a";

        return """
            <!DOCTYPE html>
            <html lang="vi">
            <head><meta charset="UTF-8"/><title>%s</title></head>
            <body style="margin:0;padding:0;background:#f4f4f4;font-family:Arial,Helvetica,sans-serif;">
              <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f4f4;padding:40px 0;">
                <tr><td align="center">
                  <table width="560" cellpadding="0" cellspacing="0"
                         style="background:#fff;border-radius:8px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,.12);">
                    <tr><td style="background:%s;padding:32px 40px;text-align:center;">
                      <h1 style="margin:0;color:#fff;font-size:24px;letter-spacing:1px;">OOP Exam System</h1>
                      <p style="margin:8px 0 0;color:rgba(255,255,255,.75);font-size:14px;">%s</p>
                    </td></tr>
                    <tr><td style="padding:40px;">
                      <h2 style="margin:0 0 8px;color:#1a1a1a;font-size:20px;">%s</h2>
                      <p style="margin:0 0 24px;color:#555;font-size:15px;">Xin chào <strong>%s</strong>,</p>
                      <table cellpadding="0" cellspacing="0" width="100%%"
                             style="background:%s;border-radius:8px;border:1px solid %s;margin-bottom:24px;">
                        <tr><td style="padding:24px;">
                          <p style="margin:0 0 8px;color:#666;font-size:14px;"><strong>Sinh viên:</strong> %s</p>
                          <p style="margin:0;color:#666;font-size:14px;"><strong>Kỳ thi:</strong> %s</p>
                        </td></tr>
                      </table>
                      <p style="margin:0;color:#555;font-size:14px;line-height:1.7;">%s</p>
                    </td></tr>
                    <tr><td style="background:#f9f9f9;padding:16px 40px;text-align:center;">
                      <p style="margin:0;color:#bbb;font-size:12px;">© 2026 OOP Exam System &middot; AGSFJOPE Team</p>
                    </td></tr>
                  </table>
                </td></tr>
              </table>
            </body>
            </html>
            """.formatted(title, headerBg, headerSub, title, recipientName,
                          boxBg, boxBorder, studentName, examName, bodyText);
    }
}
