package com.garmentfactory.integration.smtp;

import com.garmentfactory.notification.api.EmailMessage;
import com.garmentfactory.notification.api.EmailSender;
import com.garmentfactory.shared.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/** Sends email over SMTP on a background thread so requests never wait for the mail server. */
@Component
@Async
public class SmtpEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final JavaMailSender mailSender;
    private final AppProperties properties;

    public SmtpEmailSender(JavaMailSender mailSender, AppProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void send(EmailMessage message) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(properties.mail().from());
        mail.setTo(message.to());
        mail.setSubject(message.subject());
        mail.setText(message.body());
        try {
            mailSender.send(mail);
        } catch (MailException ex) {
            // Only the exception type is logged: the message may contain the recipient address.
            log.warn("Email delivery failed: {}", ex.getClass().getSimpleName());
        }
    }
}