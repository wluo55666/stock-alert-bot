package com.weiluo.marketalert.model;

import org.ta4j.core.Bar;

public record SymbolBar(String symbol, Bar bar) {
}
