package com.uba.mbp.integration.icad.client;

import com.uba.mbp.integration.icad.clearance.ClearanceRequest;
import com.uba.mbp.integration.icad.config.IcadProperties;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

/**
 * ADR-0014: models NIBSS ICAD's real pushAccount/fetchAccount operations —
 * banks push a customer account record to the industry database and later
 * fetch it back to confirm processing — but this project has no real ICAD
 * sandbox to verify the shape against, unlike Fineract standing in for
 * Finacle/Vision (ADR-0012/0013). Every environment this client talks to in
 * this repo is a WireMock stub of the contract below; swapping to a real
 * ICAD sandbox later means replacing this class only, not the clearance
 * domain logic or the {@code IcadClearanceOutcomeEvent} contract.
 */
@Component
public class IcadClient {

    private final ProducerTemplate producerTemplate;
    private final IcadProperties properties;
    private final ObjectMapper objectMapper;

    public IcadClient(ProducerTemplate producerTemplate, IcadProperties properties, ObjectMapper objectMapper) {
        this.producerTemplate = producerTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public IcadPushResult pushAccount(ClearanceRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("accountNumber", request.accountNumber());
        body.put("customerId", request.customerId());
        body.put("customerName", request.customerName());
        body.put("bvn", request.bvn());
        body.put("clearedDate", request.clearedDate() == null ? null : request.clearedDate().toString());

        JsonNode response = postJson("/icad/v1/accounts", body);
        return new IcadPushResult(response.path("reference").asString(""), response.path("status").asString("PENDING"));
    }

    public IcadFetchResult fetchAccount(String reference) {
        JsonNode response = getJson("/icad/v1/accounts/" + reference);
        String detail = response.path("detail").isMissingNode() ? null : response.path("detail").asString(null);
        return new IcadFetchResult(response.path("reference").asString(reference), response.path("status").asString("PENDING"), detail);
    }

    private JsonNode postJson(String path, Object body) {
        Map<String, Object> headers = Map.of(
                Exchange.HTTP_METHOD, "POST",
                Exchange.CONTENT_TYPE, "application/json",
                "X-ICAD-Api-Key", properties.getApiKey());
        String requestJson = objectMapper.writeValueAsString(body);
        String responseJson = producerTemplate.requestBodyAndHeaders(
                properties.getBaseUrl() + path, requestJson, headers, String.class);
        return objectMapper.readTree(responseJson);
    }

    private JsonNode getJson(String path) {
        Map<String, Object> headers = Map.of(
                Exchange.HTTP_METHOD, "GET",
                "X-ICAD-Api-Key", properties.getApiKey());
        String responseJson = producerTemplate.requestBodyAndHeaders(
                properties.getBaseUrl() + path, null, headers, String.class);
        return objectMapper.readTree(responseJson);
    }
}
