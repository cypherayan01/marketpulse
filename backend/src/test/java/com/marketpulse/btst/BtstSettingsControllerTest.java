package com.marketpulse.btst;

import com.marketpulse.config.MarketPulseProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BtstSettingsController.class)
@EnableConfigurationProperties(MarketPulseProperties.class)
@TestPropertySource(properties = "marketpulse.dashboard.token=dash-token")
class BtstSettingsControllerTest {

    private static final String AUTH = "Bearer dash-token";

    @Autowired private MockMvc mvc;
    @MockitoBean private BtstPreferences preferences;

    @Test
    void returnsTheSavedPriceRange() throws Exception {
        when(preferences.priceRange()).thenReturn(new PriceRange(100.0, 800.0));

        mvc.perform(get("/api/settings/btst").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minPrice").value(100.0))
                .andExpect(jsonPath("$.maxPrice").value(800.0))
                .andExpect(jsonPath("$.label").value("₹100 - ₹800"));
    }

    @Test
    void savesANewPriceRange() throws Exception {
        mvc.perform(put("/api/settings/btst").header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"minPrice\":null,\"maxPrice\":1500}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxPrice").value(1500.0))
                .andExpect(jsonPath("$.label").value("up to ₹1,500"));

        verify(preferences).savePriceRange(new PriceRange(null, 1500.0));
    }

    @Test
    void rejectsARangeThatCannotMatchAnything() throws Exception {
        mvc.perform(put("/api/settings/btst").header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"minPrice\":900,\"maxPrice\":100}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("Minimum")));

        verify(preferences, never()).savePriceRange(any());
    }

    @Test
    void needsTheDashboardToken() throws Exception {
        mvc.perform(get("/api/settings/btst")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/settings/btst").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
