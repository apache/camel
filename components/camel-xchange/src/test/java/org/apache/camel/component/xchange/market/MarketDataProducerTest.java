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
package org.apache.camel.component.xchange.market;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Assert;
import org.junit.Test;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.Ticker;

import static org.apache.camel.component.xchange.XChangeConfiguration.HEADER_CURRENCY_PAIR;

public class MarketDataProducerTest extends CamelTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                
                from("direct:ticker")
                    .to("xchange:binance?service=marketdata&method=ticker");
                
                from("direct:tickerBTCUSDT")
                    .to("xchange:binance?service=marketdata&method=ticker&currencyPair=BTC/USDT");
            }
        };
    }

    @Test
    public void testTicker() throws Exception {
        
        Ticker ticker = template.requestBody("direct:ticker", CurrencyPair.EOS_ETH, Ticker.class);
        Assert.assertNotNull("Ticker not null", ticker);
        
        ticker = template.requestBodyAndHeader("direct:ticker", null, HEADER_CURRENCY_PAIR, CurrencyPair.EOS_ETH, Ticker.class);
        Assert.assertNotNull("Ticker not null", ticker);
    }

    @Test
    public void testTickerBTCUSDT() throws Exception {
        
        Ticker ticker = template.requestBody("direct:tickerBTCUSDT", null, Ticker.class);
        Assert.assertNotNull("Ticker not null", ticker);
    }
}

