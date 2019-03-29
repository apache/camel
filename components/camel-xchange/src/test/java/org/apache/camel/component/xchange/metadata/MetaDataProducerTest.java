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
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Assert;
import org.junit.Test;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.meta.CurrencyMetaData;
import org.knowm.xchange.dto.meta.CurrencyPairMetaData;

import static org.apache.camel.component.xchange.XChangeConfiguration.HEADER_CURRENCY;
import static org.apache.camel.component.xchange.XChangeConfiguration.HEADER_CURRENCY_PAIR;

public class MetaDataProducerTest extends CamelTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                
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
    public void testCurrencies() throws Exception {
        
        List<Currency> currencies = template.requestBody("direct:currencies", null, List.class);
        Assert.assertNotNull("Currencies not null", currencies);
        Assert.assertTrue("Contains ETH", currencies.contains(Currency.ETH));
    }

    @Test
    public void testCurrencyMetaData() throws Exception {
        
        CurrencyMetaData metadata = template.requestBody("direct:currencyMetaData", Currency.ETH, CurrencyMetaData.class);
        Assert.assertNotNull("CurrencyMetaData not null", metadata);
        
        metadata = template.requestBodyAndHeader("direct:currencyMetaData", null, HEADER_CURRENCY, Currency.ETH, CurrencyMetaData.class);
        Assert.assertNotNull("CurrencyMetaData not null", metadata);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testCurrencyPairs() throws Exception {
        
        List<CurrencyPair> pairs = template.requestBody("direct:currencyPairs", null, List.class);
        Assert.assertNotNull("Pairs not null", pairs);
        Assert.assertTrue("Contains EOS/ETH", pairs.contains(CurrencyPair.EOS_ETH));
    }

    @Test
    public void testCurrencyPairMetaData() throws Exception {
        
        CurrencyPairMetaData metadata = template.requestBody("direct:currencyPairMetaData", CurrencyPair.EOS_ETH, CurrencyPairMetaData.class);
        Assert.assertNotNull("CurrencyPairMetaData not null", metadata);
        
        metadata = template.requestBodyAndHeader("direct:currencyPairMetaData", null, HEADER_CURRENCY_PAIR, CurrencyPair.EOS_ETH, CurrencyPairMetaData.class);
        Assert.assertNotNull("CurrencyPairMetaData not null", metadata);
    }
}

