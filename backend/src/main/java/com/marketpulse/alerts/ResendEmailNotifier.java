package com.marketpulse.alerts;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/** Sends alerts by email through the Resend HTTP API (works on hosts that block SMTP ports). */
public class ResendEmailNotifier implements Notifier {

    private final RestClient client;
    private final String from;
    private final String to;

    public ResendEmailNotifier(RestClient.Builder builder, String apiKey, String from, String to) {
        this.client = builder.baseUrl("https://api.resend.com")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
        this.from = from;
        this.to = to;
    }

    @Override
    public void send(String subject, String body) {
        client.post()
                .uri("/emails")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("from", from, "to", List.of(to), "subject", subject, "text", body))
                .retrieve()
                .toBodilessEntity();
    }
}
