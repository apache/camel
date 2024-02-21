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
package org.apache.camel.component.xchange.metadata;

import java.util.List;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.xchange.XChangeTestSupport;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.meta.CurrencyMetaData;
import org.knowm.xchange.dto.meta.InstrumentMetaData;

import static org.apache.camel.component.xchange.XChangeConfiguration.HEADER_CURRENCY;
import static org.apache.camel.component.xchange.XChangeConfiguration.HEADER_CURRENCY_PAIR;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Disabled("See CAMEL-19751 before enabling")
public class MetaDataProducerTest extends XChangeTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {

                from("direct:currencies")
                        .to("xchange:binance?service=metadata&method=currencies");

                from("direct:currencyMetaData")
                        .to("xchange:binance?service=metadata&method=currencyMetaData");

                from("direct:currencyPairs")
                        .to("xchange:binance?service=metadata&method=currencyPairs");

                from("direct:currencyPairMetaData")
                        .to("xchange:binance?service=metadata&method=currencyPairMetaData");
            }
        };
    }

    @Test
    @SuppressWarnings("unchecked")
    void testCurrencies() {

        List<Currency> currencies = template.requestBody("direct:currencies", null, List.class);
        assertNotNull(currencies, "Currencies not null");
        assertTrue(currencies.contains(Currency.ETH), "Contains ETH");
    }

    @Test
    void testCurrencyMetaData() {

        CurrencyMetaData metadata = template.requestBody("direct:currencyMetaData", Currency.ETH, CurrencyMetaData.class);
        assertNotNull(metadata, "CurrencyMetaData not null");

        metadata = template.requestBodyAndHeader("direct:currencyMetaData", null, HEADER_CURRENCY, Currency.ETH,
                CurrencyMetaData.class);
        assertNotNull(metadata, "CurrencyMetaData not null");
    }

    @Test
    @SuppressWarnings("unchecked")
    void testCurrencyPairs() {

        List<CurrencyPair> pairs = template.requestBody("direct:currencyPairs", null, List.class);
        assertNotNull(pairs, "Pairs not null");
        assertTrue(pairs.contains(CurrencyPair.EOS_ETH), "Contains EOS/ETH");
    }

    @Test
    void testCurrencyPairMetaData() {

        InstrumentMetaData metadata
                = template.requestBody("direct:currencyPairMetaData", CurrencyPair.EOS_ETH, InstrumentMetaData.class);
        assertNotNull(metadata, "CurrencyPairMetaData not null");

        metadata = template.requestBodyAndHeader("direct:currencyPairMetaData", null, HEADER_CURRENCY_PAIR,
                CurrencyPair.EOS_ETH, InstrumentMetaData.class);
        assertNotNull(metadata, "CurrencyPairMetaData not null");
    }
}
