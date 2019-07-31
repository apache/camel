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
package org.apache.camel.component.weather;

import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public abstract class BaseWeatherConsumerTest extends CamelTestSupport {

    protected void checkWeatherContent(String weather) {
        // the default mode is json
        log.debug("The weather in {} format is {}{}", WeatherMode.JSON, LS, weather);

        assertStringContains(weather, "\"coord\":{");
        assertStringContains(weather, "temp");
    }

    @Test
    public void testGrabbingListOfEntries() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        // as the default delay option is one hour long, we expect exactly one message exchange
        mock.expectedMessageCount(1);

        // give the route a bit time to start and fetch the weather info
        assertMockEndpointsSatisfied(20, TimeUnit.SECONDS);

        Exchange exchange = mock.getExchanges().get(0);
        assertNotNull(exchange);
        Message in = exchange.getIn();
        assertNotNull(in);
        String weather = assertIsInstanceOf(String.class, in.getBody());

        checkWeatherContent(weather);
    }

}
