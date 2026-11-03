/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.xchange;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.meta.CurrencyMetaData;
import org.knowm.xchange.dto.meta.ExchangeMetaData;
import org.knowm.xchange.dto.meta.InstrumentMetaData;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.service.marketdata.MarketDataService;

import static org.apache.camel.component.xchange.XChangeConfiguration.HEADER_CURRENCY;
import static org.apache.camel.component.xchange.XChangeConfiguration.HEADER_CURRENCY_PAIR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the producers against a fake exchange, so the currency and the currency pair given as text are tested without
 * calling a real exchange.
 */
public class XChangeCurrencyConversionTest extends CamelTestSupport {

    private static final CurrencyMetaData BTC_META = new CurrencyMetaData(8, BigDecimal.ZERO);
    private static final CurrencyMetaData USDT_META = new CurrencyMetaData(2, BigDecimal.ONE);
    private static final InstrumentMetaData BTC_USDT_META = InstrumentMetaData.builder().priceScale(2).build();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        XChangeComponent component = new XChangeComponent();
        component.putXChange("fake", new XChange(fakeExchange()));
        context.addComponent("xchange", component);
        return context;
    }

    private static Exchange fakeExchange() {
        ExchangeMetaData metaData = new ExchangeMetaData(
                Map.of(CurrencyPair.BTC_USDT, BTC_USDT_META),
                Map.of(Currency.BTC, BTC_META, Currency.USDT, USDT_META), null, null, false);
        MarketDataService marketData = (MarketDataService) Proxy.newProxyInstance(
                XChangeCurrencyConversionTest.class.getClassLoader(), new Class<?>[] { MarketDataService.class },
                (proxy, method, args) -> {
                    if ("getTicker".equals(method.getName())) {
                        return new Ticker.Builder().instrument((Instrument) args[0]).last(BigDecimal.TEN).build();
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        return (Exchange) Proxy.newProxyInstance(
                XChangeCurrencyConversionTest.class.getClassLoader(), new Class<?>[] { Exchange.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "getExchangeMetaData" -> metaData;
                    case "getMarketDataService" -> marketData;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:ticker")
                        .to("xchange:fake?service=marketdata&method=ticker");
                from("direct:currencyMetaData")
                        .to("xchange:fake?service=metadata&method=currencyMetaData");
                from("direct:currencyPairMetaData")
                        .to("xchange:fake?service=metadata&method=currencyPairMetaData");
            }
        };
    }

    @Test
    void testCurrencyOption() {
        Object body = template.requestBody("xchange:fake?service=metadata&method=currencyMetaData&currency=BTC", (Object) null);
        assertSame(BTC_META, body);
    }

    @Test
    void testCurrencyHeaderAsText() {
        Object body = template.requestBodyAndHeader("direct:currencyMetaData", null, HEADER_CURRENCY, "USDT");
        assertSame(USDT_META, body);
    }

    @Test
    void testCurrencyBodyAsText() {
        Object body = template.requestBody("direct:currencyMetaData", "btc");
        assertSame(BTC_META, body);
    }

    @Test
    void testUnknownCurrency() {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.requestBodyAndHeader("direct:currencyMetaData", null, HEADER_CURRENCY, "NOSUCHCOIN"));
        assertTrue(e.getCause().getMessage().contains("NOSUCHCOIN"), e.getCause().getMessage());
    }

    @Test
    void testCurrencyPairHeaderAsText() {
        Ticker ticker = template.requestBodyAndHeader("direct:ticker", null, HEADER_CURRENCY_PAIR, "BTC/USDT", Ticker.class);
        assertNotNull(ticker);
        assertEquals(CurrencyPair.BTC_USDT, ticker.getInstrument());
    }

    @Test
    void testCurrencyPairBodyAsText() {
        Object body = template.requestBody("direct:currencyPairMetaData", "BTC/USDT");
        assertSame(BTC_USDT_META, body);
    }

    @Test
    void testCurrencyPairTextWithoutSeparator() {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.requestBody("direct:currencyPairMetaData", "BTCUSDT"));
        assertTrue(e.getCause().getMessage().contains("BTCUSDT"), e.getCause().getMessage());
    }

    @Test
    void testNoCurrencyPair() {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.requestBody("direct:currencyPairMetaData", (Object) null));
        assertInstanceOf(IllegalArgumentException.class, e.getCause());
        assertEquals("Null currency pair", e.getCause().getMessage());
    }

    @Test
    void testMethodOfAnotherService() {
        ResolveEndpointFailedException e = assertThrows(ResolveEndpointFailedException.class,
                () -> context.getEndpoint("xchange:fake?service=marketdata&method=currencies"));
        assertEquals("The method currencies belongs to the metadata service, not to the marketdata service",
                e.getCause().getMessage());
    }

    @Test
    void testNoMethod() {
        ResolveEndpointFailedException e = assertThrows(ResolveEndpointFailedException.class,
                () -> context.getEndpoint("xchange:fake?service=metadata"));
        assertEquals("The service and method options are required", e.getCause().getMessage());
    }
}
