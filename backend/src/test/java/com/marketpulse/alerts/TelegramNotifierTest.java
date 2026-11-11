package com.marketpulse.alerts;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TelegramNotifierTest {

    @Test
    void postsMessageToBotApi() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.telegram.org/botSECRET/sendMessage"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.chat_id").value("12345"))
                .andExpect(jsonPath("$.text").value("Subject\n\nBody"))
                .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

        new TelegramNotifier(builder, "SECRET", "12345").send("Subject", "Body");

        server.verify();
    }

    @Test
    void splitsLongMessagesAtLineBreaksUnderTelegramLimit() {
        String line = "x".repeat(99) + "\n"; // 100 chars per line
        String body = line.repeat(90);         // 9,000 chars -> needs 3 messages of <= 4,000

        java.util.List<String> chunks = TelegramNotifier.chunks("Subject", body);

        org.assertj.core.api.Assertions.assertThat(chunks).hasSize(3);
        org.assertj.core.api.Assertions.assertThat(chunks).allMatch(c -> c.length() <= 4000);
        org.assertj.core.api.Assertions.assertThat(chunks.getFirst()).startsWith("Subject\n\n");
        org.assertj.core.api.Assertions.assertThat(String.join("", chunks).replace("Subject\n\n", ""))
                .isEqualTo(body);
    }
}
