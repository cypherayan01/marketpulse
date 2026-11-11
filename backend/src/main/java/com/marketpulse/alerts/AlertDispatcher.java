package com.marketpulse.alerts;

import com.marketpulse.news.NewsImpact;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/** Sends an alert to every configured channel; one failing channel never blocks the others. */
public class AlertDispatcher {

    private static final Logger log = LoggerFactory.getLogger(AlertDispatcher.class);

    private final List<Notifier> notifiers;

    public AlertDispatcher(List<Notifier> notifiers) {
        this.notifiers = notifiers;
    }

    /** @return true if at least one channel delivered the alert */
    public boolean dispatch(NewsImpact impact) {
        return send(AlertFormatter.subject(impact), AlertFormatter.body(impact));
    }

    /** Sends a ready-made message (reports, briefs). @return true if at least one channel delivered it */
    public boolean send(String subject, String body) {
        boolean delivered = false;
        for (Notifier notifier : notifiers) {
            try {
                notifier.send(subject, body);
                delivered = true;
            } catch (RuntimeException e) {
                log.warn("{} failed to send alert: {}", notifier.getClass().getSimpleName(), e.getMessage());
            }
        }
        return delivered;
    }
}
