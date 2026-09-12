package com.uba.mbp.memobalance.web;

import com.uba.mbp.memobalance.domain.AdjustmentType;

import java.math.BigDecimal;

public record AdjustmentRequest(AdjustmentType type, BigDecimal amount) {
}
