package com.marketpulse.alerts;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.Map;

/** Sends alerts through a Telegram bot (create one with @BotFather). */
public class TelegramNotifier implements Notifier {

    private final RestClient client;
    private final String botToken;
    private final String chatId;

    public TelegramNotifier(RestClient.Builder builder, String botToken, String chatId) {
        this.client = builder.baseUrl("https://api.telegram.org").build();
        this.botToken = botToken;
        this.chatId = chatId;
    }

    @Override
    public void send(String subject, String body) {
        client.post()
                .uri("/bot{token}/sendMessage", botToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("chat_id", chatId, "text", subject + "\n\n" + body, "disable_web_page_preview", true))
                .retrieve()
                .toBodilessEntity();
    }
}
