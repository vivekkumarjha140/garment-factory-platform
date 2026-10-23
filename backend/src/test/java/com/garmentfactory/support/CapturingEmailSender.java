package com.garmentfactory.support;

import com.garmentfactory.notification.api.EmailMessage;
import com.garmentfactory.notification.api.EmailSender;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** Replaces the SMTP adapter in tests and lets them read the links that would have been emailed. */
@Component
@Primary
public class CapturingEmailSender implements EmailSender {

    private static final Pattern TOKEN = Pattern.compile("token=([A-Za-z0-9_-]+)");

    private final List<EmailMessage> sent = new CopyOnWriteArrayList<>();

    @Override
    public void send(EmailMessage message) {
        sent.add(message);
    }

    public List<EmailMessage> sentTo(String address) {
        return sent.stream().filter(message -> message.to().equals(address)).toList();
    }

    public String latestTokenTo(String address) {
        List<EmailMessage> messages = sentTo(address);
        for (int i = messages.size() - 1; i >= 0; i--) {
            Matcher matcher = TOKEN.matcher(messages.get(i).body());
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        throw new IllegalStateException("No email with a token was sent to " + address);
    }
}