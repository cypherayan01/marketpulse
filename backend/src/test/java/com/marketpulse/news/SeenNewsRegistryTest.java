package com.marketpulse.news;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SeenNewsRegistryTest {

    @Test
    void remembersMarkedIds() {
        SeenNewsRegistry registry = new SeenNewsRegistry(10);

        registry.markSeen("a");

        assertThat(registry.hasSeen("a")).isTrue();
        assertThat(registry.hasSeen("b")).isFalse();
    }

    @Test
    void forgetsOldestWhenCapacityExceeded() {
        SeenNewsRegistry registry = new SeenNewsRegistry(2);

        registry.markSeen("a");
        registry.markSeen("b");
        registry.markSeen("c");

        assertThat(registry.hasSeen("a")).isFalse();
        assertThat(registry.hasSeen("b")).isTrue();
        assertThat(registry.hasSeen("c")).isTrue();
    }
}
