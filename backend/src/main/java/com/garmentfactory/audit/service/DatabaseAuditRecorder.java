package com.garmentfactory.audit.service;

import com.garmentfactory.audit.api.AuditEvent;
import com.garmentfactory.audit.api.AuditRecorder;
import com.garmentfactory.audit.entity.AuditLog;
import com.garmentfactory.audit.repository.AuditLogRepository;
import com.garmentfactory.shared.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Stores events in the audit_logs table inside the caller's transaction, so an action and its
 * audit row are committed or rolled back together. Actor, IP and user agent are taken from the
 * current request when the event does not name an actor.
 */
@Service
public class DatabaseAuditRecorder implements AuditRecorder {

    private static final int MAX_DETAILS = 1000;

    private final AuditLogRepository repository;

    public DatabaseAuditRecorder(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void record(AuditEvent event) {
        Long actor = event.actorUserId() != null ? event.actorUserId() : currentUserId();
        String ip = null;
        String userAgent = null;
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servlet) {
            HttpServletRequest request = servlet.getRequest();
            ip = request.getRemoteAddr();
            userAgent = truncate(request.getHeader("User-Agent"), 255);
        }
        repository.save(new AuditLog(actor, event.action(), event.entityType(), event.entityId(),
                truncate(event.details(), MAX_DETAILS), ip, userAgent));
    }

    private static Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user.id();
        }
        return null;
    }

    private static String truncate(String value, int max) {
        return value != null && value.length() > max ? value.substring(0, max) : value;
    }
}