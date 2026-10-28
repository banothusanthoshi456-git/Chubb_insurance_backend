package com.example.claims.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record DashboardResponse(
    long totalClaims,
    Map<String, Long> claimsByStatus,
    BigDecimal outstandingLiabilityExposure,
    BigDecimal totalApprovedAmount,
    List<MarketExposure> exposureByMarket
) {
    public record MarketExposure(String market, long openClaims, BigDecimal exposure) {}
}
