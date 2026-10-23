package com.garmentfactory.notification.api;

/** Plain-text email. The body may contain secrets (links), so it must never be logged. */
public record EmailMessage(String to, String subject, String body) {

    @Override
    public String toString() {
        return "EmailMessage[subject=" + subject + "]";
    }
}