package com.helphub.identity.infrastructure.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import org.springframework.stereotype.Component;

import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class PasswordRecoveryMailListener {

    private static final Logger log = LoggerFactory.getLogger(
            PasswordRecoveryMailListener.class);

    private final JavaMailSender mailSender;
    private final String from;

    public PasswordRecoveryMailListener(
            JavaMailSender mailSender,
            @Value("${app.mail.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    // ==========================================
    // Password Reset OTP
    // ==========================================

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendPasswordResetOtp(
            PasswordResetEmailEvent event) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();

            message.setFrom(from);
            message.setTo(event.email());

            message.setSubject(
                    "HelpHub - Password Reset Verification");

            message.setText(
                    """
                            You have requested to reset your HelpHub password.

                            Your verification code is: %s

                            This code expires in 5 minutes.

                            If you did not request this change,
                            you can safely ignore this email.

                            HelpHub Security Team
                            """.formatted(event.otp()));

            mailSender.send(message);

        } catch (MailException ex) {
            // Never log OTP or full email content.
            log.error(
                    "Password reset OTP email delivery failed",
                    ex);
        }
    }

    // ==========================================
    // Password Changed Notification
    // ==========================================

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendPasswordChangedNotification(
            PasswordChangedEmailEvent event) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();

            message.setFrom(from);
            message.setTo(event.email());

            message.setSubject(
                    "HelpHub - Your Password Has Been Changed");

            message.setText(
                    """
                            Your HelpHub account password
                            was successfully changed.

                            All previous login sessions
                            have been revoked.

                            If you did not perform this action,
                            please contact HelpHub support.

                            HelpHub Security Team
                            """);

            mailSender.send(message);

        } catch (MailException ex) {
            log.error(
                    "Password change notification delivery failed",
                    ex);
        }
    }
}