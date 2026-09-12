package com.uba.mbp.integration.writeoffdetection.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The RFP §3.13(bis) detection rule itself — kept separate from
 * {@link FineractProperties} (Fineract *connection* details) since the two
 * change for different reasons: this one changes when the business narration
 * rule changes, that one changes when the Fineract/Finacle endpoint changes.
 */
@ConfigurationProperties(prefix = "write-off.detection")
public class WriteOffDetectionProperties {

    /** Case-insensitive, with configurable variants ("written-off", "write off", ...). */
    private String pattern = "writ(?:e|ten)[- ]?off";

    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }
}
