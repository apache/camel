/**
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
package org.apache.camel.component.weather;

import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.knowm.xchange.dto.marketdata.Ticker;

public class TickerConsumerTest extends CamelTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("xchange:binance?method=ticker&currencyPair=BTC/USDT")
                        .to("mock:result");
            }
        };
    }

    @Test
    public void testTicker() throws Exception {
        
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        // give the route a bit time to start and fetch the ticker info
        assertMockEndpointsSatisfied(20, TimeUnit.SECONDS);

        Exchange exchange = mock.getExchanges().get(0);
        Ticker ticker = exchange.getIn().getBody(Ticker.class);
        assertNotNull(ticker);
    }
}

