package com.marketpulse.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.marketpulse.alerts.AlertDispatcher;
import com.marketpulse.alerts.LoggingNotifier;
import com.marketpulse.alerts.Notifier;
import com.marketpulse.alerts.ResendEmailNotifier;
import com.marketpulse.alerts.TelegramNotifier;
import com.marketpulse.market.MarketDataClient;
import com.marketpulse.market.YahooChartClient;
import com.marketpulse.news.ClaudeNewsImpactAnalyzer;
import com.marketpulse.news.NewsAlertService;
import com.marketpulse.news.NewsAlertSettings;
import com.marketpulse.news.NewsImpactAnalyzer;
import com.marketpulse.news.NewsRelevanceFilter;
import com.marketpulse.news.NewsSource;
import com.marketpulse.news.RssNewsSource;
import com.marketpulse.news.SeenNewsRegistry;
import com.marketpulse.swing.EodWatchlistService;
import com.marketpulse.swing.IndicatorCalculator;
import com.marketpulse.swing.SwingScorer;
import com.marketpulse.swing.SwingSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableConfigurationProperties(MarketPulseProperties.class)
public class AppConfig {

    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);
    private static final String USER_AGENT = "Mozilla/5.0 (compatible; MarketPulse/1.0)";

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    NewsAlertService newsAlertService(MarketPulseProperties props, NewsImpactAnalyzer analyzer,
                                      AlertDispatcher alertDispatcher, Clock clock) {
        MarketPulseProperties.News news = props.news();
        RestClient feedClient = restClientBuilder()
                .defaultHeader("User-Agent", USER_AGENT)
                .build();
        List<NewsSource> sources = news.feeds().stream()
                .<NewsSource>map(f -> new RssNewsSource(f.name(), f.url(), feedClient))
                .toList();
        log.info("News alerter configured with {} feeds, threshold {}/10", sources.size(), news.impactThreshold());

        return new NewsAlertService(
                sources,
                new NewsRelevanceFilter(news.keywords(), news.watchlist()),
                new SeenNewsRegistry(5_000),
                analyzer,
                alertDispatcher,
                clock,
                new NewsAlertSettings(news.lookback(), news.impactThreshold(), news.batchSize()));
    }

    @Bean
    NewsImpactAnalyzer newsImpactAnalyzer(MarketPulseProperties props) {
        String apiKey = MarketPulseProperties.hasText(props.claude().apiKey())
                ? props.claude().apiKey()
                : System.getenv("ANTHROPIC_API_KEY");
        if (!MarketPulseProperties.hasText(apiKey)) {
            log.warn("No Claude API key configured (ANTHROPIC_API_KEY) - news analysis is disabled");
            return items -> {
                throw new IllegalStateException("ANTHROPIC_API_KEY is not set");
            };
        }
        AnthropicClient client = AnthropicOkHttpClient.builder().apiKey(apiKey).build();
        return new ClaudeNewsImpactAnalyzer(client, props.claude().triageModel());
    }

    @Bean
    MarketDataClient marketDataClient() {
        return new YahooChartClient(restClientBuilder().defaultHeader("User-Agent", USER_AGENT));
    }

    @Bean
    EodWatchlistService eodWatchlistService(MarketPulseProperties props, MarketDataClient market,
                                            AlertDispatcher alertDispatcher, Clock clock) {
        MarketPulseProperties.Swing swing = props.swing();
        log.info("Swing scanner configured with {} stocks", swing.universe().size());
        return new EodWatchlistService(market, new IndicatorCalculator(), new SwingScorer(), alertDispatcher, clock,
                new SwingSettings(swing.universe(), swing.indexSymbol(), swing.topN()));
    }

    @Bean
    AlertDispatcher alertDispatcher(MarketPulseProperties props) {
        List<Notifier> notifiers = new ArrayList<>();
        if (props.telegram().configured()) {
            notifiers.add(new TelegramNotifier(restClientBuilder(), props.telegram().botToken(), props.telegram().chatId()));
        }
        if (props.email().configured()) {
            notifiers.add(new ResendEmailNotifier(restClientBuilder(), props.email().resendApiKey(),
                    props.email().from(), props.email().to()));
        }
        if (notifiers.isEmpty()) {
            log.warn("No Telegram or email configured - alerts will only be logged");
            notifiers.add(new LoggingNotifier());
        }
        return new AlertDispatcher(notifiers);
    }

    private static RestClient.Builder restClientBuilder() {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(20));
        return RestClient.builder().requestFactory(factory);
    }
}
