package com.marketpulse.alerts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Fallback channel when no Telegram/email is configured: alerts appear in the logs. */
public class LoggingNotifier implements Notifier {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotifier.class);

    @Override
    public void send(String subject, String body) {
        log.info("ALERT {}\n{}", subject, body);
    }
}
