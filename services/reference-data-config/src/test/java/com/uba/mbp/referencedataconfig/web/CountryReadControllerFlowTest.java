package com.uba.mbp.referencedataconfig.web;

import com.uba.mbp.referencedataconfig.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADR-0021: the read path needs no token at all — the V1 migration's seeded
 * NG row (matching StaticCountryConfigLookup's old values exactly) proves
 * the swap-the-implementation promise memo-balance's interim seam made.
 */
@AutoConfigureMockMvc
class CountryReadControllerFlowTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getCurrentReturnsTheSeededNigeriaMappingWithNoAuthentication() throws Exception {
        mockMvc.perform(get("/countries/NG"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.countryCode").value("NG"))
                .andExpect(jsonPath("$.baseCurrency").value("NGN"))
                .andExpect(jsonPath("$.glWriteOffCode").value("GL-WRITEOFF-NG"))
                .andExpect(jsonPath("$.glRecoveryCode").value("GL-RECOVERY-NG"));
    }

    @Test
    void getCurrentIsNotFoundForAnUnconfiguredCountry() throws Exception {
        mockMvc.perform(get("/countries/ZZ"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listCurrentIncludesTheSeededNigeriaRow() throws Exception {
        mockMvc.perform(get("/countries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.countryCode == 'NG')]").exists());
    }
}
