package com.translator.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class TranslateService {

    private final OkHttpClient client;
    private final Gson gson;
    // URL API
    private static final String API_URL = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl=ru&dt=t&q=";

    public TranslateService() {
        // Таймауты увеличены для VPN и медленного интернета
        this.client = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(40, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
        this.gson = new Gson();
    }

    /**
     * Пытается перевести текст 3 раза перед тем как сдаться.
     * Обрабатывает 429 ошибку (Too Many Requests).
     */
    public String translateWithRetry(String text) {
        int maxRetries = 3;

        for (int i = 0; i < maxRetries; i++) {
            String result = translate(text);

            // Если успех
            if (result != null) {
                return result;
            }

            // Если неудача, ждем перед следующей попыткой
            if (i < maxRetries - 1) {
                long waitTime = 2000 * (i + 1); // 2сек, 4сек...
                System.out.print(" (R" + (i+1) + ") "); // Индикатор повтора в логе
                try {
                    Thread.sleep(waitTime);
                } catch (InterruptedException ignored) {}
            }
        }
        return text; // Возвращаем оригинал, если ничего не помогло
    }

    // Базовый метод перевода (Теперь public, чтобы тесты не ругались)
    public String translate(String text) {
        if (text == null || text.trim().isEmpty()) return text;

        try {
            // Случайная пауза 100-300мс (имитация человека)
            Thread.sleep(ThreadLocalRandom.current().nextInt(100, 300));

            String encodedText = URLEncoder.encode(text, StandardCharsets.UTF_8);
            Request request = new Request.Builder()
                    .url(API_URL + encodedText)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    return parseGoogleResponse(response.body().string());
                } else if (response.code() == 429) {
                    System.err.println("⚠️ Google Rate Limit (429).");
                    return null; // Вернет null -> сработает Retry
                } else {
                    System.err.println("Ошибка HTTP: " + response.code());
                }
            }
        } catch (Exception e) {
            System.err.println("Ошибка сети: " + e.getMessage());
        }
        return null;
    }

    private String parseGoogleResponse(String jsonResponse) {
        try {
            StringBuilder result = new StringBuilder();
            JsonArray rootArray = gson.fromJson(jsonResponse, JsonArray.class);
            JsonArray sentences = rootArray.get(0).getAsJsonArray();
            for (int i = 0; i < sentences.size(); i++) {
                result.append(sentences.get(i).getAsJsonArray().get(0).getAsString());
            }
            return result.toString();
        } catch (Exception e) {
            return null;
        }
    }
}