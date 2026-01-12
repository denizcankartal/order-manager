package com.ordermanager.model.filter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Base class for Binance symbol filters.
 *
 * Only the filters needed for order validation are mapped (PRICE_FILTER,
 * LOT_SIZE, MIN_NOTIONAL).
 *
 * https://developers.binance.com/docs/binance-spot-api-docs/filters#symbol-filters
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "filterType", defaultImpl = UnknownFilter.class)
@JsonSubTypes({
        @JsonSubTypes.Type(value = PriceFilter.class, name = "PRICE_FILTER"),
        @JsonSubTypes.Type(value = LotSizeFilter.class, name = "LOT_SIZE"),
        @JsonSubTypes.Type(value = MinNotionalFilter.class, name = "MIN_NOTIONAL"),
        @JsonSubTypes.Type(value = MinNotionalFilter.class, name = "NOTIONAL")
})
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class SymbolFilter {

    /**
     * Get the filter type name.
     *
     * @return Filter type (e.g., "PRICE_FILTER", "LOT_SIZE")
     */
    public abstract String getFilterType();
}
