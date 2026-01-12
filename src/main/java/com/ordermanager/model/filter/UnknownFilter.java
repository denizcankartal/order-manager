package com.ordermanager.model.filter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Placeholder for unknown Binance filter types.
 *
 * Used as default implementation for filters we don't need to validate against
 * (e.g., ICEBERG_PARTS, MARKET_LOT_SIZE, MAX_NUM_ORDERS, etc.).
 *
 * This allows graceful handling of new filter types without breaking
 * deserialization.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class UnknownFilter extends SymbolFilter {

    public UnknownFilter() {
    }

    @Override
    public String getFilterType() {
        return "UNKNOWN";
    }
}
