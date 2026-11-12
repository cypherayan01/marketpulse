package com.marketpulse.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.marketpulse.alerts.AlertDispatcher;
import com.marketpulse.analysis.ClaudeStockExplainer;
import com.marketpulse.analysis.StockAnalyzer;
import com.marketpulse.analysis.StockExplainer;
import com.marketpulse.alerts.LoggingNotifier;
import com.marketpulse.alerts.Notifier;
import com.marketpulse.alerts.ResendEmailNotifier;
import com.marketpulse.alerts.TelegramNotifier;
import com.marketpulse.brief.BriefSettings;
import com.marketpulse.brief.BriefWriter;
import com.marketpulse.brief.ClaudeBriefWriter;
import com.marketpulse.brief.CueDefinition;
import com.marketpulse.brief.PreMarketBriefService;
import com.marketpulse.btst.BtstCalculator;
import com.marketpulse.btst.BtstScanService;
import com.marketpulse.btst.BtstScorer;
import com.marketpulse.btst.BtstSettings;
import com.marketpulse.events.EventCalendar;
import com.marketpulse.events.Impact;
import com.marketpulse.events.MarketEvent;
import com.marketpulse.events.Source;
import com.marketpulse.market.CachedMarketData;
import com.marketpulse.market.MarketDataClient;
import com.marketpulse.market.YahooChartClient;
import com.marketpulse.universe.Universe;
import com.marketpulse.news.ClaudeNewsImpactAnalyzer;
import com.marketpulse.news.NewsAlertService;
import com.marketpulse.news.NewsAlertSettings;
import com.marketpulse.news.NewsImpactAnalyzer;
import com.marketpulse.news.NewsRelevanceFilter;
import com.marketpulse.news.NewsSource;
import com.marketpulse.news.RssNewsSource;
import com.marketpulse.news.SeenNewsRegistry;
import com.marketpulse.positional.PositionalCalculator;
import com.marketpulse.positional.PositionalScanService;
import com.marketpulse.positional.PositionalScorer;
import com.marketpulse.positional.PositionalSettings;
import com.marketpulse.holdings.HoldingEvaluator;
import com.marketpulse.holdings.HoldingRepository;
import com.marketpulse.holdings.HoldingsCheckService;
import com.marketpulse.holdings.JdbcHoldingRepository;
import com.marketpulse.persistence.JdbcReportArchive;
import com.marketpulse.predictions.JdbcPredictionRepository;
import com.marketpulse.predictions.OutcomeCalculator;
import com.marketpulse.predictions.PredictionRecorder;
import com.marketpulse.predictions.PredictionRepository;
import com.marketpulse.predictions.PredictionUpdater;
import com.marketpulse.predictions.ReplayService;
import com.marketpulse.predictions.ReplaySettings;
import com.marketpulse.persistence.JdbcResultsDateRepository;
import com.marketpulse.reports.ReportStore;
import com.marketpulse.tracking.JdbcSignalRepository;
import com.marketpulse.tracking.PaperTradeEvaluator;
import com.marketpulse.tracking.SignalRecorder;
import com.marketpulse.tracking.SignalRepository;
import com.marketpulse.tracking.SignalTracker;
import com.marketpulse.swing.EodWatchlistService;
import com.marketpulse.swing.IndicatorCalculator;
import com.marketpulse.swing.SwingScorer;
import com.marketpulse.swing.SwingSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Configuration
@EnableConfigurationProperties(MarketPulseProperties.class)
public class AppConfig {

    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);
    private static final String USER_AGENT = "Mozilla/5.0 (compatible; MarketPulse/1.0)";

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    // ---------- Phase 7: persistence and paper-trade tracking ----------

    @Bean
    SignalRepository signalRepository(JdbcClient jdbc) {
        return new JdbcSignalRepository(jdbc);
    }

    @Bean
    JdbcResultsDateRepository resultsDateRepository(JdbcClient jdbc) {
        return new JdbcResultsDateRepository(jdbc);
    }

    /** Dashboard reports, restored from the database on startup; every bullish setup becomes a paper trade. */
    @Bean
    ReportStore reportStore(JdbcClient jdbc, JsonMapper json, SignalRepository signals, Universe universe,
                            PredictionRepository predictions, MarketDataClient market, MarketPulseProperties props) {
        ReportStore store = new ReportStore(200, new JdbcReportArchive(jdbc, json));
        store.onScan(new SignalRecorder(signals, universe::segmentOf));
        // Every buy and avoid call is also recorded, to measure how it turned out vs Nifty.
        store.onScan(new PredictionRecorder(predictions, market, props.swing().indexSymbol(), universe::segmentOf, store));
        return store;
    }

    @Bean
    PredictionRepository predictionRepository(JdbcClient jdbc, NamedParameterJdbcTemplate named) {
        return new JdbcPredictionRepository(jdbc, named);
    }

    @Bean
    PredictionUpdater predictionUpdater(PredictionRepository predictions, MarketDataClient market, Clock clock,
                                        MarketPulseProperties props) {
        return new PredictionUpdater(predictions, market, new OutcomeCalculator(), props.swing().indexSymbol(), clock);
    }

    /** One year of history, the same 6 calls per section as the reports, 3 strong BTST calls per section. */
    @Bean
    ReplayService replayService(MarketDataClient market, Universe universe, PredictionRepository predictions,
                                Clock clock, MarketPulseProperties props) {
        return new ReplayService(market, universe, props.swing().indexSymbol(), predictions, new OutcomeCalculator(),
                clock, new ReplaySettings(250, props.swing().topN(), props.btst().topN()));
    }

    @Bean
    SignalTracker signalTracker(SignalRepository signals, MarketDataClient market) {
        return new SignalTracker(signals, market, new PaperTradeEvaluator());
    }

    /** Holder beans: Spring treats List<T> and Optional<T> injection points specially, so wrap them. */
    record NewsFeeds(List<NewsSource> sources) {
    }

    record Claude(Optional<AnthropicClient> client) {
    }

    // ---------- shared building blocks ----------

    @Bean
    NewsFeeds newsFeeds(MarketPulseProperties props) {
        RestClient feedClient = restClientBuilder().defaultHeader("User-Agent", USER_AGENT).build();
        return new NewsFeeds(props.news().feeds().stream()
                .<NewsSource>map(f -> new RssNewsSource(f.name(), f.url(), feedClient))
                .toList());
    }

    @Bean
    NewsRelevanceFilter newsRelevanceFilter(MarketPulseProperties props) {
        return new NewsRelevanceFilter(props.news().keywords(), props.news().watchlist());
    }

    @Bean
    EventCalendar eventCalendar(MarketPulseProperties props, JdbcResultsDateRepository resultsDates) {
        List<MarketEvent> seed = new ArrayList<>();
        props.events().macro().forEach(e -> seed.add(
                MarketEvent.macro(e.date(), e.title(), Impact.valueOf(e.impact().toUpperCase(Locale.ROOT)))));
        props.events().results().forEach(r -> seed.add(MarketEvent.results(r.date(), r.symbol(), Source.CONFIG)));
        List<MarketEvent> fromNews = resultsDates.findAll();
        seed.addAll(fromNews);
        log.info("Event calendar seeded with {} events ({} results dates remembered from news)", seed.size(), fromNews.size());
        EventCalendar calendar = new EventCalendar(seed);
        calendar.onNewsResults(e -> resultsDates.save(e.symbol(), e.date()));
        return calendar;
    }

    /** One Yahoo client for daily and intraday data, so its request spacing covers both. */
    @Bean
    YahooChartClient yahooChartClient() {
        return new YahooChartClient(restClientBuilder().defaultHeader("User-Agent", USER_AGENT));
    }

    /**
     * Daily candles as the scanners see them: cached for 20 minutes, so the swing scan, tracking and holdings
     * check that run back to back after the close fetch each of ~500 stocks once.
     */
    @Bean
    @Primary
    MarketDataClient marketData(YahooChartClient yahoo, Clock clock) {
        return new CachedMarketData(yahoo, Duration.ofMinutes(20), clock);
    }

    /** Stocks to scan: the bundled NSE index lists (Nifty 500 split into large / mid / small / other) or your own list. */
    @Bean
    Universe universe(MarketPulseProperties props) {
        List<String> custom = props.swing().universe();
        Universe universe = custom.isEmpty() ? Universe.loadBundled() : Universe.unsegmented(custom);
        log.info("Scanning {} stocks ({})", universe.symbols().size(), custom.isEmpty() ? "Nifty 500 by market cap" : "custom list");
        return universe;
    }

    /** Empty when no API key is configured - AI features then degrade gracefully. */
    @Bean
    Claude claude(MarketPulseProperties props) {
        String apiKey = MarketPulseProperties.hasText(props.claude().apiKey())
                ? props.claude().apiKey()
                : System.getenv("ANTHROPIC_API_KEY");
        if (!MarketPulseProperties.hasText(apiKey)) {
            log.warn("No Claude API key configured (ANTHROPIC_API_KEY) - news analysis and AI briefs are disabled");
            return new Claude(Optional.empty());
        }
        return new Claude(Optional.of(AnthropicOkHttpClient.builder().apiKey(apiKey).build()));
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

    // ---------- Phase 1: news alerts ----------

    @Bean
    NewsImpactAnalyzer newsImpactAnalyzer(Claude claude, MarketPulseProperties props) {
        return claude.client().<NewsImpactAnalyzer>map(c -> new ClaudeNewsImpactAnalyzer(c, props.claude().triageModel()))
                .orElse(items -> {
                    throw new IllegalStateException("ANTHROPIC_API_KEY is not set");
                });
    }

    @Bean
    NewsAlertService newsAlertService(MarketPulseProperties props, NewsFeeds feeds,
                                      NewsRelevanceFilter filter, NewsImpactAnalyzer analyzer,
                                      AlertDispatcher alertDispatcher, EventCalendar calendar, Clock clock,
                                      ReportStore store) {
        MarketPulseProperties.News news = props.news();
        log.info("News alerter configured with {} feeds, threshold {}/10", feeds.sources().size(), news.impactThreshold());
        return new NewsAlertService(feeds.sources(), filter, new SeenNewsRegistry(5_000), analyzer, alertDispatcher,
                calendar, clock, new NewsAlertSettings(news.lookback(), news.impactThreshold(), news.batchSize()), store);
    }

    // ---------- Phase 2: swing watchlist ----------

    @Bean
    EodWatchlistService eodWatchlistService(MarketPulseProperties props, MarketDataClient market, Universe universe,
                                            AlertDispatcher alertDispatcher, EventCalendar calendar, Clock clock,
                                            ReportStore store) {
        MarketPulseProperties.Swing swing = props.swing();
        return new EodWatchlistService(market, new IndicatorCalculator(), new SwingScorer(), alertDispatcher, calendar,
                clock, new SwingSettings(universe, swing.indexSymbol(), swing.topN(), swing.resultsWarningDays()), store);
    }

    // ---------- Phase 3: pre-market brief ----------

    @Bean
    PreMarketBriefService preMarketBriefService(MarketPulseProperties props, YahooChartClient market,
                                                NewsFeeds feeds, NewsRelevanceFilter filter,
                                                EventCalendar calendar, Claude claude,
                                                AlertDispatcher alertDispatcher, Clock clock, ReportStore store) {
        MarketPulseProperties.Brief brief = props.brief();
        BriefWriter writer = claude.client().<BriefWriter>map(c -> new ClaudeBriefWriter(c, props.claude().briefModel()))
                .orElse(input -> {
                    throw new IllegalStateException("ANTHROPIC_API_KEY is not set");
                });
        List<CueDefinition> cues = brief.cues().stream().map(c -> new CueDefinition(c.name(), c.symbol())).toList();
        return new PreMarketBriefService(market, cues, feeds.sources(), filter, calendar, writer, alertDispatcher, clock,
                new BriefSettings(new HashSet<>(props.events().holidays()), brief.maxHeadlines(), brief.eventDays()), store);
    }

    // ---------- Phase 4: positional weekly scan ----------

    @Bean
    PositionalScanService positionalScanService(MarketPulseProperties props, MarketDataClient market,
                                                Universe universe, AlertDispatcher alertDispatcher,
                                                EventCalendar calendar, Clock clock, ReportStore store) {
        MarketPulseProperties.Positional positional = props.positional();
        Universe scanned = positional.universe().isEmpty() ? universe : Universe.unsegmented(positional.universe());
        return new PositionalScanService(market, new PositionalCalculator(), new PositionalScorer(), alertDispatcher,
                calendar, clock, new PositionalSettings(scanned, props.swing().indexSymbol(), positional.topN(),
                positional.resultsWarningDays()), store);
    }

    // ---------- Phase 5: BTST 3 PM scan ----------

    @Bean
    BtstScanService btstScanService(MarketPulseProperties props, YahooChartClient yahoo, Universe universe,
                                    AlertDispatcher alertDispatcher, EventCalendar calendar, Clock clock,
                                    ReportStore store) {
        MarketPulseProperties.Btst btst = props.btst();
        Universe scanned = btst.universe().isEmpty() ? universe : Universe.unsegmented(btst.universe());
        // Today's candle is still forming at 3 PM, so BTST reads Yahoo directly rather than the 20-minute cache.
        return new BtstScanService(yahoo, yahoo, new BtstCalculator(), new BtstScorer(), alertDispatcher, calendar,
                clock, new BtstSettings(scanned, props.swing().indexSymbol(), btst.topN(), btst.intradayPerSegment()),
                store);
    }

    // ---------- Holdings health check ----------

    @Bean
    HoldingRepository holdingRepository(JdbcClient jdbc) {
        return new JdbcHoldingRepository(jdbc);
    }

    @Bean
    HoldingsCheckService holdingsCheckService(HoldingRepository holdings, MarketDataClient market,
                                              EventCalendar calendar, AlertDispatcher alertDispatcher,
                                              ReportStore store, Clock clock, MarketPulseProperties props) {
        return new HoldingsCheckService(holdings, market, new HoldingEvaluator(), calendar, alertDispatcher, store,
                clock, props.swing().indexSymbol());
    }

    // ---------- Analyse a stock ----------

    @Bean
    StockAnalyzer stockAnalyzer(MarketDataClient market, Universe universe, EventCalendar calendar, ReportStore store,
                                Clock clock, MarketPulseProperties props) {
        return new StockAnalyzer(market, universe, calendar, store, clock, props.swing().indexSymbol());
    }

    @Bean
    StockExplainer stockExplainer(Claude claude, MarketPulseProperties props) {
        return claude.client().<StockExplainer>map(c -> new ClaudeStockExplainer(c, props.claude().briefModel()))
                .orElse(analysis -> {
                    throw new IllegalStateException("ANTHROPIC_API_KEY is not set");
                });
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
