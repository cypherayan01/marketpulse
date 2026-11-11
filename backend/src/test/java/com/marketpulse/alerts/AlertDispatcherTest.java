package com.marketpulse.alerts;

import com.marketpulse.news.Direction;
import com.marketpulse.news.NewsImpact;
import com.marketpulse.news.NewsItem;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AlertDispatcherTest {

    @Test
    void failingNotifierDoesNotBlockOthers() {
        List<String> delivered = new ArrayList<>();
        Notifier broken = (subject, body) -> { throw new IllegalStateException("telegram down"); };
        Notifier working = (subject, body) -> delivered.add(subject);

        boolean sent = new AlertDispatcher(List.of(broken, working)).dispatch(impact());

        assertThat(delivered).hasSize(1);
        assertThat(sent).isTrue();
    }

    @Test
    void reportsFailureWhenEveryNotifierFails() {
        Notifier broken = (subject, body) -> { throw new IllegalStateException("down"); };

        assertThat(new AlertDispatcher(List.of(broken)).dispatch(impact())).isFalse();
    }

    private static NewsImpact impact() {
        return new NewsImpact(new NewsItem("1", "RBI cuts rates", "", "https://x", "Test", Instant.now()),
                9, Direction.BULLISH, List.of("Banking"), List.of(), "Lower rates help lenders.");
    }
}
