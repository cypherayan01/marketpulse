package com.marketpulse.alerts;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
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

    /** Telegram rejects messages over 4,096 characters; stay safely below it. */
    static final int MAX_MESSAGE = 4_000;

    @Override
    public void send(String subject, String body) {
        for (String chunk : chunks(subject, body)) {
            client.post()
                    .uri("/bot{token}/sendMessage", botToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("chat_id", chatId, "text", chunk, "disable_web_page_preview", true))
                    .retrieve()
                    .toBodilessEntity();
        }
    }

    /** Splits the message at line breaks into pieces Telegram accepts. */
    static List<String> chunks(String subject, String body) {
        String text = subject + "\n\n" + body;
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : text.split("(?<=\n)")) {
            while (line.length() > MAX_MESSAGE) { // a single huge line: hard split
                flush(chunks, current);
                chunks.add(line.substring(0, MAX_MESSAGE));
                line = line.substring(MAX_MESSAGE);
            }
            if (current.length() + line.length() > MAX_MESSAGE) {
                flush(chunks, current);
            }
            current.append(line);
        }
        flush(chunks, current);
        return chunks;
    }

    private static void flush(List<String> chunks, StringBuilder current) {
        if (!current.isEmpty()) {
            chunks.add(current.toString());
            current.setLength(0);
        }
    }
}
