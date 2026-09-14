package com.uba.mbp.referencedataconfig.web;

import com.uba.mbp.referencedataconfig.service.CountryConfigService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The cross-context read path (spec User Stories 3, 5, 7) — deliberately not
 * RBAC-gated, same trust model as notification-service's {@code POST
 * /notifications} (ADR-0019): called service-to-service by other backend
 * contexts (memo-balance today), not by a human or the {@code channels} SPA.
 */
@RestController
public class CountryReadController {

    private final CountryConfigService service;

    public CountryReadController(CountryConfigService service) {
        this.service = service;
    }

    @GetMapping("/countries/{code}")
    public CountryResponse getCurrent(@PathVariable("code") String countryCode) {
        return CountryResponse.from(service.findCurrent(countryCode));
    }

    @GetMapping("/countries")
    public List<CountryResponse> listCurrent() {
        return service.listCurrent().stream().map(CountryResponse::from).toList();
    }
}
