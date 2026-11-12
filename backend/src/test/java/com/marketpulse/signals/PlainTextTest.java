package com.marketpulse.signals;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlainTextTest {

    @Test
    void removesMarkdownThatWouldShowUpAsSymbolsInTelegramAndTheDashboard() {
        String markdown = """
                **Bottom line:** Keep your 20 shares.

                ## Why
                - Price broke above **₹1,324.50** on __1.8x__ volume
                * `RSI` is 69
                """;

        assertThat(PlainText.from(markdown)).isEqualTo("""
                Bottom line: Keep your 20 shares.

                Why
                - Price broke above ₹1,324.50 on 1.8x volume
                - RSI is 69""");
    }

    @Test
    void leavesPlainTextAlone() {
        String plain = "Mood: Mixed.\\nKey drivers:\\n- Crude -> OMCs 3*2 risk";

        assertThat(PlainText.from(plain)).isEqualTo(plain);
    }
}
