package agsfjope.backend.infrastructure.email;

import agsfjope.backend.application.ports.out.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * SMTP implementation of EmailService using Spring's JavaMailSender.
 * Configured via spring.mail.* properties in application.yml (Gmail SMTP).
 * Falls back to console logging so developers can inspect the email HTML
 * even without a working SMTP connection.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SmtpEmailService implements EmailService {

  private final JavaMailSender mailSender;

  // ─────────────────────────────────────────────────────────────────────────
  // 1. Password Reset Email
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Sends a branded HTML password-reset email to the given address.
   * The email contains an orange CTA button with the reset link.
   *
   * @param to        recipient's email address
   * @param resetLink full reset-password URL (e.g.
   *                  http://localhost:5173/reset-password?token=...)
   */
  @Override
  public void sendPasswordResetEmail(String to, String resetLink) {
    String subject = "[OOP Exam] Đặt lại mật khẩu của bạn";
    String htmlContent = buildResetPasswordEmailTemplate(resetLink);

    // Always log to console so devs can preview the email during development
    log.info("=== [EMAIL] Sending password reset email ===");
    log.info("To      : {}", to);
    log.info("Subject : {}", subject);
    log.info("Link    : {}", resetLink);
    log.info("HTML:\n{}", htmlContent);
    log.info("============================================");

    try {
      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
      helper.setTo(to);
      helper.setSubject(subject);
      helper.setText(htmlContent, true); // true = isHtml
      mailSender.send(message);
      log.info("[EMAIL] Password reset email sent successfully to {}", to);
    } catch (MessagingException e) {
      log.error("[EMAIL] Failed to send password reset email to {}: {}", to, e.getMessage());
      throw new RuntimeException("Không thể gửi email đặt lại mật khẩu: " + e.getMessage(), e);
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // 2. Account Activation Email
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Sends a branded HTML account-activation email to the newly registered
   * student.
   * The email contains a green CTA button linking to the verification page.
   *
   * @param to             recipient's email address (the student's FPT email)
   * @param activationLink full verify-account URL (e.g.
   *                       http://localhost:5173/verify-account?token=...)
   */
  @Override
  public void sendActivationEmail(String to, String activationLink) {
    String subject = "[OOP Exam] Kích hoạt tài khoản của bạn";
    String htmlContent = buildActivationEmailTemplate(activationLink);

    log.info("=== [EMAIL] Sending activation email ===");
    log.info("To      : {}", to);
    log.info("Subject : {}", subject);
    log.info("Link    : {}", activationLink);
    log.info("HTML:\n{}", htmlContent);
    log.info("=========================================");

    try {
      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
      helper.setTo(to);
      helper.setSubject(subject);
      helper.setText(htmlContent, true);
      mailSender.send(message);
      log.info("[EMAIL] Activation email sent successfully to {}", to);
    } catch (MessagingException e) {
      log.error("[EMAIL] Failed to send activation email to {}: {}", to, e.getMessage());
      throw new RuntimeException("Không thể gửi email kích hoạt tài khoản: " + e.getMessage(), e);
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // 3. Account Credentials Email (Admin bulk import)
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Sends a credential notification email to a student created via Admin Excel
   * import.
   * The email delivers the auto-generated username and default password,
   * and reminds the student to change their password on first login to activate
   * the account.
   *
   * @param to             recipient's FPT email address
   * @param username       the auto-generated username (e.g. duyntse170601)
   * @param password       the plain-text default password shown once in the email
   * @param activationLink the verify-account URL to embed in the email
   */
  @Override
  public void sendAccountCredentialsEmail(String to, String username, String password, String activationLink) {
    String subject = "[OOP Exam] Tài khoản của bạn đã được tạo";
    String htmlContent = buildAccountCredentialsEmailTemplate(username, password, activationLink);

    log.info("=== [EMAIL] Sending account credentials email ===");
    log.info("To       : {}", to);
    log.info("Username : {}", username);
    log.info("Subject  : {}", subject);
    log.info("Link     : {}", activationLink);
    log.info("=================================================");

    try {
      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
      helper.setTo(to);
      helper.setSubject(subject);
      helper.setText(htmlContent, true);
      mailSender.send(message);
      log.info("[EMAIL] Credentials email sent successfully to {}", to);
    } catch (MessagingException e) {
      log.error("[EMAIL] Failed to send credentials email to {}: {}", to, e.getMessage());
      throw new RuntimeException("Không thể gửi email thông tin tài khoản: " + e.getMessage(), e);
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Private HTML Template Builders
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
        <head>
          <meta charset="UTF-8" />
          <meta name="viewport" content="width=device-width, initial-scale=1.0" />
          <title>Đặt lại mật khẩu</title>
        </head>
        <body style="margin:0;padding:0;background:#f4f4f4;font-family:Arial,Helvetica,sans-serif;">
          <table width="100%%" cellpadding="0" cellspacing="0"
                 style="background:#f4f4f4;padding:40px 0;">
            <tr>
              <td align="center">
                <table width="560" cellpadding="0" cellspacing="0"
                       style="background:#ffffff;border-radius:8px;overflow:hidden;
                              box-shadow:0 2px 8px rgba(0,0,0,.12);">

                  <!-- ── HEADER ── -->
                  <tr>
                    <td style="background:#f37120;padding:32px 40px;text-align:center;">
                      <h1 style="margin:0;color:#ffffff;font-size:24px;letter-spacing:1px;">
                        OOP Exam System
                      </h1>
                      <p style="margin:8px 0 0;color:#ffe0c4;font-size:14px;">
                        Hệ thống Chấm điểm Bài thi Lập trình
                      </p>
                    </td>
                  </tr>

                  <!-- ── BODY ── -->
                  <tr>
                    <td style="padding:40px 40px 24px;">
                      <h2 style="margin:0 0 16px;color:#1a1a1a;font-size:20px;">
                        Đặt lại mật khẩu của bạn
                      </h2>
                      <p style="margin:0 0 16px;color:#555;font-size:15px;line-height:1.7;">
                        Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản của bạn.<br/>
                        Nhấn vào nút bên dưới để tiến hành tạo mật khẩu mới:
                      </p>

                      <!-- CTA BUTTON -->
                      <table cellpadding="0" cellspacing="0" style="margin:24px 0;">
                        <tr>
                          <td style="background:#f37120;border-radius:6px;">
                            <a href="%s"
                               style="display:inline-block;padding:14px 36px;
                                      color:#ffffff;font-size:16px;font-weight:bold;
                                      text-decoration:none;border-radius:6px;">
                              &#128274;&nbsp;&nbsp;Đặt lại mật khẩu
                            </a>
                          </td>
                        </tr>
                      </table>

                      <p style="margin:0 0 8px;color:#888;font-size:13px;">
                        ⏰ Link sẽ <strong>hết hạn sau 15 phút</strong> kể từ khi email được gửi.
                      </p>
                      <p style="margin:0;color:#888;font-size:13px;">
                        Nếu bạn không yêu cầu đặt lại mật khẩu, vui lòng bỏ qua email này.
                      </p>
                    </td>
                  </tr>

                  <!-- ── DIVIDER ── -->
                  <tr>
                    <td style="padding:0 40px;">
                      <hr style="border:none;border-top:1px solid #eeeeee;margin:0;" />
                    </td>
                  </tr>

                  <!-- ── FALLBACK LINK ── -->
                  <tr>
                    <td style="padding:16px 40px 32px;">
                      <p style="margin:0;color:#aaa;font-size:12px;line-height:1.6;">
                        Nếu nút không hoạt động, hãy sao chép URL dưới đây vào trình duyệt:<br/>
                        <a href="%s" style="color:#f37120;word-break:break-all;">%s</a>
                      </p>
                    </td>
                  </tr>

                  <!-- ── FOOTER ── -->
                  <tr>
                    <td style="background:#f9f9f9;padding:16px 40px;text-align:center;">
                      <p style="margin:0;color:#bbb;font-size:12px;">
                        © 2026 OOP Exam System &middot; AGSFJOPE Team
                      </p>
                    </td>
                  </tr>

                </table>
              </td>
            </tr>
          </table>
        </body>
        </html>
        """.formatted(resetLink, resetLink, resetLink);
  }

  /**
   * Builds a branded green HTML template for the account-activation email.
   * Green (#16a34a) is used to emphasize the positive "activate account" action.
   *
   * @param activationLink the URL containing the activation JWT token
   * @return HTML string ready to be sent as email body
   */
  private String buildActivationEmailTemplate(String activationLink) {
    return """
        <!DOCTYPE html>
        <html lang="vi">
        <head>
          <meta charset="UTF-8" />
          <meta name="viewport" content="width=device-width, initial-scale=1.0" />
          <title>Kích hoạt tài khoản</title>
        </head>
        <body style="margin:0;padding:0;background:#f4f4f4;font-family:Arial,Helvetica,sans-serif;">
          <table width="100%%" cellpadding="0" cellspacing="0"
                 style="background:#f4f4f4;padding:40px 0;">
            <tr>
              <td align="center">
                <table width="560" cellpadding="0" cellspacing="0"
                       style="background:#ffffff;border-radius:8px;overflow:hidden;
                              box-shadow:0 2px 8px rgba(0,0,0,.12);">

                  <!-- ── HEADER ── -->
                  <tr>
                    <td style="background:#f37120;padding:32px 40px;text-align:center;">
                      <h1 style="margin:0;color:#ffffff;font-size:24px;letter-spacing:1px;">
                        OOP Exam System
                      </h1>
                      <p style="margin:8px 0 0;color:#ffe0c4;font-size:14px;">
                        Hệ thống Chấm điểm Bài thi Lập trình
                      </p>
                    </td>
                  </tr>

                  <!-- ── BODY ── -->
                  <tr>
                    <td style="padding:40px 40px 24px;">
                      <h2 style="margin:0 0 16px;color:#1a1a1a;font-size:20px;">
                        Chào mừng bạn đến với OOP Exam! 🎉
                      </h2>
                      <p style="margin:0 0 16px;color:#555;font-size:15px;line-height:1.7;">
                        Tài khoản của bạn đã được tạo thành công.<br/>
                        Nhấn vào nút bên dưới để <strong>kích hoạt tài khoản</strong>
                        và bắt đầu sử dụng hệ thống:
                      </p>

                      <!-- CTA BUTTON — green for positive action -->
                      <table cellpadding="0" cellspacing="0" style="margin:24px 0;">
                        <tr>
                          <td style="background:#16a34a;border-radius:6px;">
                            <a href="%s"
                               style="display:inline-block;padding:14px 36px;
                                      color:#ffffff;font-size:16px;font-weight:bold;
                                      text-decoration:none;border-radius:6px;">
                              &#9989;&nbsp;&nbsp;Kích hoạt tài khoản
                            </a>
                          </td>
                        </tr>
                      </table>

                      <p style="margin:0 0 8px;color:#888;font-size:13px;">
                        ⏰ Link sẽ <strong>hết hạn sau 24 giờ</strong> kể từ khi email được gửi.
                      </p>
                      <p style="margin:0;color:#888;font-size:13px;">
                        Nếu bạn không thực hiện đăng ký, vui lòng bỏ qua email này.
                      </p>
                    </td>
                  </tr>

                  <!-- ── DIVIDER ── -->
                  <tr>
                    <td style="padding:0 40px;">
                      <hr style="border:none;border-top:1px solid #eeeeee;margin:0;" />
                    </td>
                  </tr>

                  <!-- ── FALLBACK LINK ── -->
                  <tr>
                    <td style="padding:16px 40px 32px;">
                      <p style="margin:0;color:#aaa;font-size:12px;line-height:1.6;">
                        Nếu nút không hoạt động, hãy sao chép URL dưới đây vào trình duyệt:<br/>
                        <a href="%s" style="color:#16a34a;word-break:break-all;">%s</a>
                      </p>
                    </td>
                  </tr>

                  <!-- ── FOOTER ── -->
                  <tr>
                    <td style="background:#f9f9f9;padding:16px 40px;text-align:center;">
                      <p style="margin:0;color:#bbb;font-size:12px;">
                        © 2026 OOP Exam System &middot; AGSFJOPE Team
                      </p>
                    </td>
                  </tr>

                </table>
              </td>
            </tr>
          </table>
        </body>
        </html>
        """.formatted(activationLink, activationLink, activationLink);
  }

  /**
   * Builds a branded blue HTML template for the account-credentials email.
   * Blue (#1d4ed8) conveys information — distinct from reset (orange) and
   * activation (green).
   * The student is reminded to change their password on first login to activate
   * Builds the account-credentials email template.
   * Giữ nguyên layout cũ (credentials box + warning box), chỉ thêm dòng activation link ở dưới.
   *
   * @param username       the auto-generated username to display
   * @param password       the plain-text default password displayed once in this email
   * @param activationLink the verify-account URL for the student to activate their account
   * @return HTML string ready to be sent as email body
   */
  private String buildAccountCredentialsEmailTemplate(String username, String password, String activationLink) {
    return """
        <!DOCTYPE html>
        <html lang="vi">
        <head>
          <meta charset="UTF-8" />
          <meta name="viewport" content="width=device-width, initial-scale=1.0" />
          <title>Thông tin tài khoản OOP Exam</title>
        </head>
        <body style="margin:0;padding:0;background:#f4f4f4;font-family:Arial,Helvetica,sans-serif;">
          <table width="100%%" cellpadding="0" cellspacing="0"
                 style="background:#f4f4f4;padding:40px 0;">
            <tr>
              <td align="center">
                <table width="560" cellpadding="0" cellspacing="0"
                       style="background:#ffffff;border-radius:8px;overflow:hidden;
                              box-shadow:0 2px 8px rgba(0,0,0,.12);">

                  <!-- ── HEADER ── -->
                  <tr>
                    <td style="background:#f37120;padding:32px 40px;text-align:center;">
                      <h1 style="margin:0;color:#ffffff;font-size:24px;letter-spacing:1px;">
                        OOP Exam System
                      </h1>
                      <p style="margin:8px 0 0;color:#ffe0c4;font-size:14px;">
                        Hệ thống Chấm điểm Bài thi Lập trình
                      </p>
                    </td>
                  </tr>

                  <!-- ── BODY ── -->
                  <tr>
                    <td style="padding:40px 40px 24px;">
                      <h2 style="margin:0 0 16px;color:#1a1a1a;font-size:20px;">
                        🎓 Tài khoản đã được tạo cho bạn
                      </h2>
                      <p style="margin:0 0 20px;color:#555;font-size:15px;line-height:1.7;">
                        Quản trị viên đã tạo tài khoản OOP Exam cho bạn.<br/>
                        Dưới đây là thông tin đăng nhập của bạn:
                      </p>

                      <!-- CREDENTIALS BOX -->
                      <table cellpadding="0" cellspacing="0" width="100%%"
                             style="margin:0 0 24px;background:#f0f7ff;border-radius:8px;
                                    border:1px solid #bfdbfe;">
                        <tr>
                          <td style="padding:20px 24px;">
                            <p style="margin:0 0 10px;font-size:14px;color:#666;">
                              <strong style="color:#1d4ed8;">Tên đăng nhập:</strong>
                              &nbsp;
                              <span style="font-family:monospace;font-size:16px;
                                          color:#1a1a1a;font-weight:bold;">%s</span>
                            </p>
                            <p style="margin:0;font-size:14px;color:#666;">
                              <strong style="color:#1d4ed8;">Mật khẩu tạm thời:</strong>
                              &nbsp;
                              <span style="font-family:monospace;font-size:16px;
                                          color:#1a1a1a;font-weight:bold;">%s</span>
                            </p>
                          </td>
                        </tr>
                      </table>

                      <!-- WARNING BOX -->
                      <table cellpadding="0" cellspacing="0" width="100%%"
                             style="margin:0 0 20px;background:#fff7ed;border-radius:8px;
                                    border-left:4px solid #f37120;">
                        <tr>
                          <td style="padding:16px 20px;">
                            <p style="margin:0;font-size:14px;color:#92400e;line-height:1.6;">
                              ⚠️ <strong>Quan trọng:</strong> Tài khoản của bạn chưa được kích hoạt.<br/>
                              Vui lòng nhấn nút bên dưới để kích hoạt trước khi đăng nhập.
                            </p>
                          </td>
                        </tr>
                      </table>

                      <!-- ACTIVATION BUTTON -->
                      <table cellpadding="0" cellspacing="0" style="margin:0 0 16px;">
                        <tr>
                          <td style="border-radius:6px;background:#16a34a;">
                            <a href="%s"
                               style="display:inline-block;padding:12px 28px;font-size:15px;
                                      font-weight:bold;color:#ffffff;text-decoration:none;
                                      border-radius:6px;">
                              ✅ Kích hoạt tài khoản
                            </a>
                          </td>
                        </tr>
                      </table>
                      <p style="margin:0 0 20px;color:#aaa;font-size:11px;word-break:break-all;">
                        Hoặc copy link: <a href="%s" style="color:#1d4ed8;">%s</a>
                        &nbsp;(hiệu lực 24 giờ)
                      </p>

                      <p style="margin:0;color:#888;font-size:13px;">
                        Nếu bạn không biết mình đã đăng ký hệ thống này, vui lòng bỏ qua email này.
                      </p>
                    </td>
                  </tr>

                  <!-- ── DIVIDER ── -->
                  <tr>
                    <td style="padding:0 40px;">
                      <hr style="border:none;border-top:1px solid #eeeeee;margin:0;" />
                    </td>
                  </tr>

                  <!-- ── FOOTER ── -->
                  <tr>
                    <td style="background:#f9f9f9;padding:16px 40px;text-align:center;">
                      <p style="margin:0;color:#bbb;font-size:12px;">
                        © 2026 OOP Exam System &middot; AGSFJOPE Team
                      </p>
                    </td>
                  </tr>

                </table>
              </td>
            </tr>
          </table>
        </body>
        </html>
        """.formatted(username, password, activationLink, activationLink, activationLink);
  }
}
