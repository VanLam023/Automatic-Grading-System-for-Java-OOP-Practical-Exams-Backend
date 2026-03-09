package agsfjope.backend.application.ports.out;

/**
 * Outgoing port interface for email delivery.
 * Following Clean Architecture: the Application layer defines this interface (the "what"),
 * and the Infrastructure layer (SmtpEmailService) provides the implementation (the "how").
 * This decoupling makes it easy to swap SMTP with SendGrid, SES, etc.
 */
public interface EmailService {

    /**
     * Sends a password-reset email to the specified address.
     * The email contains a branded HTML template with a CTA button linking to the reset page.
     *
     * @param to        the recipient's email address
     * @param resetLink the full URL of the reset-password page (e.g. http://localhost:5173/reset-password?token=...)
     */
    void sendPasswordResetEmail(String to, String resetLink);

    /**
     * Sends an account-activation email to the newly registered user.
     * The email contains a CTA button linking to the email-verification page.
     *
     * @param to             the recipient's email address (the student's FPT email)
     * @param activationLink the full URL (e.g. http://localhost:5173/verify-account?token=...)
     */
    void sendActivationEmail(String to, String activationLink);
}
