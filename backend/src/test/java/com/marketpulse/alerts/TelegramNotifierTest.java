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
}
