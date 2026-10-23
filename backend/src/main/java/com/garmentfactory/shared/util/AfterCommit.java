package com.garmentfactory.shared.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Runs a side effect (for example sending an email) only if the surrounding transaction commits. */
public final class AfterCommit {

    private static final Logger log = LoggerFactory.getLogger(AfterCommit.class);

    private AfterCommit() {
    }

    public static void run(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    execute(action);
                }
            });
        } else {
            execute(action);
        }
    }

    private static void execute(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException ex) {
            log.warn("After-commit action failed: {}", ex.getClass().getSimpleName());
        }
    }
}