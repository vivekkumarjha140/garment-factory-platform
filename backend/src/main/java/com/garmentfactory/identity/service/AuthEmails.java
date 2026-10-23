package com.garmentfactory.identity.service;

import com.garmentfactory.notification.api.EmailMessage;
import com.garmentfactory.shared.config.AppProperties;
import org.springframework.stereotype.Component;

/** Builds the plain-text emails sent by the identity module. */
@Component
public class AuthEmails {

    private final AppProperties properties;

    public AuthEmails(AppProperties properties) {
        this.properties = properties;
    }

    public EmailMessage verification(String to, String fullName, String rawToken) {
        String link = properties.frontendBaseUrl() + "/verify-email?token=" + rawToken;
        return new EmailMessage(to, "Verify your email address",
                "Hello " + fullName + ",\n\nPlease confirm your email address to activate your account:\n"
                        + link + "\n\nThis link is valid for 24 hours. If you did not create an account, ignore this email.\n");
    }

    public EmailMessage alreadyRegistered(String to, String fullName) {
        return new EmailMessage(to, "Sign-up attempt for your account",
                "Hello " + fullName + ",\n\nSomeone tried to register with this email address, but an account already exists.\n"
                        + "You can sign in, or reset your password at " + properties.frontendBaseUrl()
                        + "/forgot-password.\nIf this was not you, no action is needed.\n");
    }

    public EmailMessage passwordReset(String to, String fullName, String rawToken) {
        String link = properties.frontendBaseUrl() + "/reset-password?token=" + rawToken;
        return new EmailMessage(to, "Reset your password",
                "Hello " + fullName + ",\n\nUse this link to choose a new password:\n" + link
                        + "\n\nThe link is valid for 30 minutes and works once. If you did not ask for this, ignore this email.\n");
    }

    public EmailMessage passwordChanged(String to, String fullName) {
        return new EmailMessage(to, "Your password was changed",
                "Hello " + fullName + ",\n\nYour password was just changed and all sessions were signed out.\n"
                        + "If this was not you, reset your password immediately at " + properties.frontendBaseUrl()
                        + "/forgot-password.\n");
    }

    public EmailMessage staffInvitation(String to, String fullName, String rawToken) {
        String link = properties.frontendBaseUrl() + "/reset-password?token=" + rawToken;
        return new EmailMessage(to, "You have been invited to the Garment Factory platform",
                "Hello " + fullName + ",\n\nAn administrator created an account for you. Choose your password here:\n"
                        + link + "\n\nThe link is valid for 48 hours and works once.\n");
    }
}