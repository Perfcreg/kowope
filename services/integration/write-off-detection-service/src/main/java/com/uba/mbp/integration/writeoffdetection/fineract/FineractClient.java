package com.uba.mbp.integration.writeoffdetection.fineract;

import com.uba.mbp.integration.writeoffdetection.config.FineractProperties;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Talks to Fineract (ADR-0012, standing in for Finacle) over HTTP, routed
 * through Camel's http component via {@link ProducerTemplate} rather than a
 * plain RestTemplate — endpoint-level timeout/connection options and Camel's
 * error handling (ADR-0011) apply to these calls the same as to any route.
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

    public List<FineractClientSummary> listActiveClients() {
        JsonNode root = getJson("/fineract-provider/api/v1/clients?status=active&limit=200");
        List<FineractClientSummary> clients = new ArrayList<>();
        for (JsonNode item : root.path("pageItems")) {
            clients.add(new FineractClientSummary(
                    item.path("id").asLong(),
                    item.path("displayName").asString(""),
                    item.path("officeName").asString("")));
        }
        return clients;
    }

    public List<Long> listSavingsAccountIds(long clientId) {
        JsonNode root = getJson("/fineract-provider/api/v1/clients/" + clientId + "/accounts");
        List<Long> ids = new ArrayList<>();
        for (JsonNode account : root.path("savingsAccounts")) {
            ids.add(account.path("id").asLong());
        }
        return ids;
    }

    public FineractSavingsAccount getSavingsAccountWithTransactions(long savingsAccountId) {
        JsonNode root = getJson("/fineract-provider/api/v1/savingsaccounts/" + savingsAccountId + "?associations=transactions");
        List<FineractSavingsTransaction> transactions = new ArrayList<>();
        for (JsonNode tx : root.path("transactions")) {
            transactions.add(new FineractSavingsTransaction(
                    tx.path("id").asLong(),
                    tx.path("note").asString(""),
                    new BigDecimal(tx.path("amount").asString("0")),
                    toLocalDate(tx.path("date"))));
        }
        return new FineractSavingsAccount(
                savingsAccountId,
                root.path("accountNo").asString(""),
                root.path("currency").path("code").asString(""),
                transactions);
    }

    private LocalDate toLocalDate(JsonNode dateArray) {
        if (!dateArray.isArray() || dateArray.size() < 3) {
            return null;
        }
        return LocalDate.of(dateArray.get(0).asInt(), dateArray.get(1).asInt(), dateArray.get(2).asInt());
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
