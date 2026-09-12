package com.uba.mbp.integration.visionetl.fineract;

import com.uba.mbp.integration.visionetl.config.FineractProperties;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Talks to Fineract (ADR-0013, standing in for Vision) over HTTP, routed
 * through Camel's http component via {@link ProducerTemplate} — same pattern
 * as write-off-detection-service's client (ADR-0011).
 */
@Component
public class FineractClient {

    private final ProducerTemplate producerTemplate;
    private final FineractProperties properties;
    private final ObjectMapper objectMapper;

    public FineractClient(ProducerTemplate producerTemplate, FineractProperties properties, ObjectMapper objectMapper) {
        this.producerTemplate = producerTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public List<Long> listActiveClientIds() {
        JsonNode root = getJson("/fineract-provider/api/v1/clients?status=active&limit=200");
        List<Long> ids = new ArrayList<>();
        for (JsonNode item : root.path("pageItems")) {
            ids.add(item.path("id").asLong());
        }
        return ids;
    }

    public List<FineractAccountBalance> listSavingsAccountBalances(long clientId) {
        JsonNode root = getJson("/fineract-provider/api/v1/clients/" + clientId + "/accounts");
        List<FineractAccountBalance> balances = new ArrayList<>();
        for (JsonNode account : root.path("savingsAccounts")) {
            balances.add(new FineractAccountBalance(
                    account.path("accountNo").asString(""),
                    new BigDecimal(account.path("accountBalance").asString("0"))));
        }
        return balances;
    }

    private JsonNode getJson(String path) {
        Map<String, Object> headers = Map.of(
                Exchange.HTTP_METHOD, "GET",
                "Authorization", basicAuthHeader(),
                "Fineract-Platform-TenantId", properties.getTenant());
        String body = producerTemplate.requestBodyAndHeaders(properties.getBaseUrl() + path, null, headers, String.class);
        return objectMapper.readTree(body);
    }

    private String basicAuthHeader() {
        String credentials = properties.getUsername() + ":" + properties.getPassword();
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }
}
