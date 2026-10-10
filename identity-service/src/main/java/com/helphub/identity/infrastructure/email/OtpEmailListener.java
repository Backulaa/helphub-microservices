package com.helphub.identity.infrastructure.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;

import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import org.springframework.stereotype.Component;

import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class OtpEmailListener {

    private static final Logger log = LoggerFactory.getLogger(OtpEmailListener.class);

    private final JavaMailSender mailSender;
    private final String from;

    public OtpEmailListener(
            JavaMailSender mailSender,
            @Value("${app.mail.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendVerificationEmail(
            OtpEmailEvent event) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();

            message.setFrom(from);
            message.setTo(event.recipient());

            message.setSubject(
                    "HelpHub - Email Verification");

            message.setText(
                    "Your HelpHub verification code is: "
                            + event.otp()
                            + "\n\nThis code expires in 5 minutes."
                            + "\nDo not share this code with anyone.");

            mailSender.send(message);

        } catch (MailException ex) {
            log.error(
                    "Unable to submit verification email to SMTP");
        }
    }
}