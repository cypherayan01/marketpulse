package com.marketpulse.alerts;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ResendEmailNotifierTest {

    @Test
    void postsEmailToResendApi() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.resend.com/emails"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer re_key"))
                .andExpect(jsonPath("$.from").value("MarketPulse <alerts@example.com>"))
                .andExpect(jsonPath("$.to[0]").value("me@example.com"))
                .andExpect(jsonPath("$.subject").value("Subject"))
                .andExpect(jsonPath("$.text").value("Body"))
                .andRespond(withSuccess("{\"id\":\"abc\"}", MediaType.APPLICATION_JSON));

        new ResendEmailNotifier(builder, "re_key", "MarketPulse <alerts@example.com>", "me@example.com")
                .send("Subject", "Body");

        server.verify();
    }
}
