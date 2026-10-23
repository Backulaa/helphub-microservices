
package com.helphub.identity.infrastructure.email;

import com.helphub.identity.otp.entity.OtpPurpose;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Component
public class OtpEmailListener {

    private static final Logger log = LoggerFactory.getLogger(OtpEmailListener.class);

    private static final DateTimeFormatter EXPIRY_FORMATTER = DateTimeFormatter.ofPattern(
            "yyyy-MM-dd HH:mm 'UTC'",
            Locale.ENGLISH);

    private final JavaMailSender mailSender;
    private final String from;

    public OtpEmailListener(
            JavaMailSender mailSender,
            @Value("${app.mail.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    // ==========================================
    // Send OTP Email After Transaction Commit
    // ==========================================

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendOtpEmail(OtpEmailEvent event) {

        EmailContent content = resolveContent(event.purpose());

        String expiresAt = event.expiresAt()
                .withOffsetSameInstant(ZoneOffset.UTC)
                .format(EXPIRY_FORMATTER);

        String body = """
                Hello,

                %s

                Your verification code is:

                %s

                This code expires in 5 minutes.
                Expiration time: %s

                %s

                For your security, never share this code with anyone,
                including HelpHub staff.

                Best regards,
                HelpHub Security Team
                """.formatted(
                content.description(),
                event.otp(),
                expiresAt,
                content.securityNotice());

        try {
            SimpleMailMessage message = new SimpleMailMessage();

            message.setFrom(from);
            message.setTo(event.recipient());

            message.setSubject(content.subject());
            message.setText(body);

            mailSender.send(message);

        } catch (MailException ex) {

            // Do not log raw OTP or email body.
            log.error(
                    "Unable to send OTP email for purpose: {}",
                    event.purpose());
        }
    }

    // ==========================================
    // Resolve Email Content By OTP Purpose
    // ==========================================

    private EmailContent resolveContent(OtpPurpose purpose) {

        return switch (purpose) {

            case EMAIL_VERIFICATION -> new EmailContent(
                    "HelpHub - Verify Your Email",
                    """
                            Thank you for registering a HelpHub account.

                            To complete your registration, please verify
                            your email address using the code below.
                            """,
                    """
                            If you did not register a HelpHub account,
                            you can safely ignore this email.
                            """);

            case PASSWORD_RESET -> new EmailContent(
                    "HelpHub - Reset Your Password",
                    """
                            We received a request to reset the password
                            for your HelpHub account.

                            Enter the code below in the Reset Password
                            form to continue.
                            """,
                    """
                            If you did not request a password reset,
                            you can safely ignore this email.
                            Your password has not been changed.
                            """);

            case MFA_LOGIN -> new EmailContent(
                    "HelpHub - Sign-In Verification Code",
                    """
                            A sign-in attempt to your HelpHub account
                            requires additional verification.

                            Enter the code below to complete the
                            multi-factor authentication step.
                            """,
                    """
                            If you did not attempt to sign in,
                            please review your account security.
                            """);

            case MFA_SETUP -> new EmailContent(
                    "HelpHub - Enable Multi-Factor Authentication",
                    """
                            We received a request to enable
                            multi-factor authentication for your
                            HelpHub account.

                            Enter the code below to confirm the setup.
                            """,
                    """
                            If you did not request this change,
                            do not share or enter this code.
                            """);

            case MFA_DISABLE -> new EmailContent(
                    "HelpHub - Disable Multi-Factor Authentication",
                    """
                            We received a request to disable
                            multi-factor authentication for your
                            HelpHub account.

                            Enter the code below to confirm
                            that you want to disable MFA.

                            Warning: Disabling MFA reduces the
                            security of your account.
                            """,
                    """
                            If you did not request this change,
                            do not share or enter this code.
                            Please review your account security.
                            """);

            default -> throw new IllegalArgumentException(
                    "Unsupported OTP email purpose: " + purpose);
        };
    }

    // ==========================================
    // Email Template Data
    // ==========================================

    private record EmailContent(
            String subject,
            String description,
            String securityNotice) {
    }
}
