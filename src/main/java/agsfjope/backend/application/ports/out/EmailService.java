package agsfjope.backend.application.ports.out;

/**
 * Outgoing port interface for email delivery.
 * Following Clean Architecture: the Application layer defines this interface
 * (the "what"),
 * and the Infrastructure layer (SmtpEmailService) provides the implementation
 * (the "how").
 * This decoupling makes it easy to swap SMTP with SendGrid, SES, etc.
 * <p>
 * SMTP configuration is read dynamically from the SystemConfigs table so
 * that Admins can change host/credentials without restarting the server (BR-51).
 * </p>
 */
public interface EmailService {

    // ─────────────────────────────────────────────────────────────────────────
    // Auth Emails
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Sends a password-reset email to the specified address.
     *
     * @param to        the recipient's email address
     * @param resetLink the full URL of the reset-password page
     */
    void sendPasswordResetEmail(String to, String resetLink);

    /**
     * Sends an account-activation email to the newly registered user.
     *
     * @param to             the recipient's email address (the student's FPT email)
     * @param activationLink the full verify-account URL
     */
    void sendActivationEmail(String to, String activationLink);

    /**
     * Sends a credential notification email to a student whose account was
     * bulk-created by Admin via Excel import.
     *
     * @param to             the student's FPT email address
     * @param username       the auto-generated username
     * @param password       the plain-text default password — shown once in email
     * @param activationLink the full verify-account URL
     */
    void sendAccountCredentialsEmail(String to, String username, String password, String activationLink);

    // ─────────────────────────────────────────────────────────────────────────
    // Notification Emails (NOTI-002 / TRG-001~006)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * TRG-001: Notifies a student that their submission has been graded.
     *
     * @param to          student's email address
     * @param studentName student's full name
     * @param examName    name of the exam (e.g. "PRO192 - Fall 2025")
     * @param blockName   block name (e.g. "Block 10")
     * @param totalScore  final score (e.g. 8.5)
     * @param isPassed    true if totalScore >= 4.0 (BR-30)
     */
    void sendGradingCompleteEmail(String to, String studentName, String examName,
                                  String blockName, double totalScore, boolean isPassed);

    /**
     * TRG-003: Confirms to a student that their appeal payment was received successfully.
     *
     * @param to            student's email address
     * @param studentName   student's full name
     * @param examName      name of the exam
     * @param amountVnd     amount paid in VND (e.g. 200000)
     * @param transactionId PayOS transaction reference ID
     */
    void sendPaymentSuccessEmail(String to, String studentName, String examName,
                                 long amountVnd, String transactionId);

    /**
     * TRG-002: Notifies a student that their appeal was approved and score updated.
     *
     * @param to          student's email address
     * @param studentName student's full name
     * @param examName    name of the exam
     * @param newScore    the updated final score after re-grading
     */
    void sendAppealApprovedEmail(String to, String studentName,
                                 String examName, double newScore);

    /**
     * TRG-002: Notifies a student that their appeal was denied (score unchanged).
     *
     * @param to               student's email address
     * @param studentName      student's full name
     * @param examName         name of the exam
     * @param lecturerComment  reviewer's comment (may be empty)
     */
    void sendAppealDeniedEmail(String to, String studentName,
                               String examName, String lecturerComment);

    /**
     * TRG-005: Notifies a lecturer that an appeal has been assigned to them.
     *
     * @param to           lecturer's email address
     * @param lecturerName lecturer's full name
     * @param studentName  student's full name (owner of the submission)
     * @param examName     name of the exam
     * @param deadline     deadline string formatted for display (e.g. "01/04/2026 23:59")
     */
    void sendAppealAssignedEmail(String to, String lecturerName, String studentName,
                                 String examName, String deadline);

    /**
     * TRG-006: Sends a deadline reminder or overdue alert for an appeal.
     * When {@code isOverdue} is false, this is a "2-day reminder" email to the lecturer.
     * When {@code isOverdue} is true, this is an overdue alert sent to both
     * the lecturer and the exam staff.
     *
     * @param to           recipient's email address
     * @param recipientName recipient's full name
     * @param studentName  student's full name (owner of the submission)
     * @param examName     name of the exam
     * @param daysLeft     days remaining before deadline (meaningful only when !isOverdue)
     * @param isOverdue    true if the deadline has already passed
     */
    void sendAppealDeadlineReminderEmail(String to, String recipientName, String studentName,
                                         String examName, int daysLeft, boolean isOverdue);
}
