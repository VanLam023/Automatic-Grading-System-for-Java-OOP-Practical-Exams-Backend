package agsfjope.backend.application.bankservices.impl;

import agsfjope.backend.application.bankservices.BankLookupService;
import agsfjope.backend.application.dtos.responses.wallet.BankOptionResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Proxy backend gọi VietQR để lấy danh sách ngân hàng.
 * Làm ở backend để tránh rủi ro CORS từ browser.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BankLookupServiceImpl implements BankLookupService {

    private static final String VIET_QR_BANKS_URL = "https://api.vietqr.io/v2/banks";
    private static HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ObjectMapper objectMapper;

    @Override
    public List<BankOptionResponse> getVietnamBanks() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(VIET_QR_BANKS_URL))
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .header("Accept", "application/json")
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("[BankLookup] VietQR returned non-success status: {}", response.statusCode());
                return List.of();
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode data = root.path("data");
            if (!data.isArray()) {
                log.warn("[BankLookup] VietQR response has no array 'data' field");
                return List.of();
            }

            List<BankOptionResponse> banks = new ArrayList<>();
            for (JsonNode item : data) {
                String code = textOrNull(item, "code");
                String name = textOrNull(item, "name");
                String shortName = textOrNull(item, "shortName");
                String bin = textOrNull(item, "bin");
                String logo = textOrNull(item, "logo");

                if (isBlank(code) && isBlank(name) && isBlank(shortName)) {
                    continue;
                }

                banks.add(BankOptionResponse.builder()
                        .code(code)
                        .name(name)
                        .shortName(shortName)
                        .bin(bin)
                        .logo(logo)
                        .build());
            }

            banks.sort(Comparator.comparing(
                    bank -> safeLower(bank.getShortName(), bank.getCode(), bank.getName())
            ));
            return banks;
        } catch (Exception e) {
            log.warn("[BankLookup] Failed to load banks from VietQR: {}", e.getMessage());
            return List.of();
        }
    }

    private String textOrNull(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText(null);
        if (text == null) {
            return null;
        }
        text = text.trim();
        return text.isEmpty() ? null : text;
    }

    private String safeLower(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim().toLowerCase();
            }
        }
        return "";
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
