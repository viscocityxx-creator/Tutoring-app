package com.tutor.app.ame.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
public class CurrencyRateService {

    private static final BigDecimal FALLBACK_RATE = BigDecimal.ONE;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, BigDecimal> cache = new HashMap<>();
    private Instant cacheExpiresAt = Instant.EPOCH;

    public BigDecimal usdTo(String currencyCode) {
        tryRefreshRates();
        return cache.getOrDefault(currencyCode.toUpperCase(), FALLBACK_RATE);
    }

    public BigDecimal convert(BigDecimal amount, String fromCurrency, String toCurrency) {
        tryRefreshRates();
        String from = fromCurrency.toUpperCase();
        String to = toCurrency.toUpperCase();
        if (from.equals(to)) {
            return amount.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal fromRate = cache.getOrDefault(from, FALLBACK_RATE);
        BigDecimal toRate = cache.getOrDefault(to, FALLBACK_RATE);
        if (fromRate.compareTo(BigDecimal.ZERO) <= 0) {
            fromRate = FALLBACK_RATE;
        }
        if (toRate.compareTo(BigDecimal.ZERO) <= 0) {
            toRate = FALLBACK_RATE;
        }

        BigDecimal inUsd = amount.divide(fromRate, 8, RoundingMode.HALF_UP);
        return inUsd.multiply(toRate).setScale(2, RoundingMode.HALF_UP);
    }

    private synchronized void tryRefreshRates() {
        if (Instant.now().isBefore(cacheExpiresAt) && !cache.isEmpty()) {
            return;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://open.er-api.com/v6/latest/USD"))
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode rates = root.path("rates");
            if (rates.isObject()) {
                cache.clear();
                rates.fields().forEachRemaining(entry -> cache.put(
                        entry.getKey().toUpperCase(),
                        new BigDecimal(entry.getValue().asText()).setScale(6, RoundingMode.HALF_UP)
                ));
                cacheExpiresAt = Instant.now().plus(Duration.ofMinutes(30));
            }
        } catch (Exception ignored) {
            cacheExpiresAt = Instant.now().plus(Duration.ofMinutes(5));
        }
    }
}
