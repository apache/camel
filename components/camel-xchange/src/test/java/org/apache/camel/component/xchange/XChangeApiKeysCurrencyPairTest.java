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

import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.camelkeys.CamelkeysExchange;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The apiKey and secretKey options reach the specification of the exchange, and the currencyPair option is checked when
 * the endpoint is created. The exchange is the camelkeys module of the tests, which makes no remote call.
 */
public class XChangeApiKeysCurrencyPairTest extends CamelTestSupport {

    private static final String TICKER = "xchange:camelkeys?service=marketdata&method=ticker";
    private static final String BALANCES = "xchange:camelkeys?service=account&method=balances";

    @Test
    void theApiKeysAreSetOnTheExchangeSpecification() {
        ExchangeSpecification specification = specification(BALANCES + "&apiKey=key-1&secretKey=secret-1");

        assertEquals("key-1", specification.getApiKey());
        assertEquals("secret-1", specification.getSecretKey());
    }

    @Test
    void endpointsWithOtherKeysGetAnExchangeOfTheirOwn() {
        XChange first = xchange(BALANCES + "&apiKey=key-1&secretKey=secret-1");
        XChange same = xchange(TICKER + "&apiKey=key-1&secretKey=secret-1&currencyPair=BTC/USDT");
        XChange other = xchange(BALANCES + "&apiKey=key-2&secretKey=secret-2");
        XChange withoutKeys = xchange(BALANCES);

        assertSame(first, same);
        assertNotSame(first, other);
        assertNotSame(first, withoutKeys);
        assertEquals("key-2", other.getExchangeSpecification().getApiKey());
        assertNull(withoutKeys.getExchangeSpecification().getApiKey());
        assertSame(withoutKeys, context.getComponent("xchange", XChangeComponent.class).getXChange("camelkeys"));
    }

    @Test
    void aCurrencyPairWithAnUnknownCurrencyFailsTheEndpoint() {
        Exception e = assertThrows(ResolveEndpointFailedException.class,
                () -> context.getEndpoint(TICKER + "&currencyPair=BTC/QQZZ"));

        assertInstanceOf(IllegalArgumentException.class, e.getCause());
        assertTrue(e.getCause().getMessage().contains("QQZZ"), e.getCause().getMessage());
        assertNull(Currency.getInstanceNoCreate("QQZZ"), "the unknown currency is not registered in XChange");
    }

    @Test
    void aCurrencyPairWithACurrencyOfTheExchangeIsAccepted() {
        XChangeEndpoint endpoint
                = context.getEndpoint(TICKER + "&currencyPair=btc/" + CamelkeysExchange.CURRENCY, XChangeEndpoint.class);

        assertEquals(new CurrencyPair(Currency.BTC, Currency.getInstance(CamelkeysExchange.CURRENCY)),
                endpoint.getConfiguration().getAsCurrencyPair());
    }

    private XChange xchange(String uri) {
        return context.getEndpoint(uri, XChangeEndpoint.class).getXchange();
    }

    private ExchangeSpecification specification(String uri) {
        return xchange(uri).getExchangeSpecification();
    }
}
