package com.marketpulse.alerts;

/** A channel that can deliver an alert to the user (Telegram, email, logs...). */
@FunctionalInterface
public interface Notifier {
    void send(String subject, String body);
}
