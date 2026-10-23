package com.garmentfactory.notification.api;

/** Port for outgoing email. Provider-specific code lives in integration.* adapters. */
public interface EmailSender {

    void send(EmailMessage message);
}