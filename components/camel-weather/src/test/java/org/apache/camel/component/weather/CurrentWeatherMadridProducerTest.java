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

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class CurrentWeatherMadridProducerTest extends BaseWeatherConsumerTest {

    @Override
    @Test
    public void testGrabbingListOfEntries() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        // as the default delay option is one hour long, we expect exactly one message exchange
        mock.expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        mock.assertIsSatisfied();

        Exchange exchange = mock.getExchanges().get(0);
        assertNotNull(exchange);
        Message in = exchange.getIn();
        assertNotNull(in);
        String weather = assertIsInstanceOf(String.class, in.getBody());

        checkWeatherContent(weather);
    }

    @Test
    public void testHeaderOverride() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        // as the default delay option is one hour long, we expect exactly one message exchange
        mock.expectedMessageCount(1);

        template.sendBodyAndHeader("direct:start", "Hello World", WeatherConstants.WEATHER_LOCATION, "Paris,France");

        mock.assertIsSatisfied();

        Exchange exchange = mock.getExchanges().get(0);
        assertNotNull(exchange);
        Message in = exchange.getIn();
        assertNotNull(in);
        String weather = assertIsInstanceOf(String.class, in.getBody());

        checkWeatherContent(weather);
    }

    @Test
    public void testHeaderOverrideLondon() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        // as the default delay option is one hour long, we expect exactly one message exchange
        mock.expectedMessageCount(1);

        template.sendBodyAndHeader("direct:start", "Hello World", WeatherConstants.WEATHER_LOCATION, "Rome,Italy");

        mock.assertIsSatisfied();

        Exchange exchange = mock.getExchanges().get(0);
        assertNotNull(exchange);
        Message in = exchange.getIn();
        assertNotNull(in);
        String weather = assertIsInstanceOf(String.class, in.getBody());

        checkWeatherContent(weather);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                
                /* The Camel Route uses the apache-camel appid to access the openweathermap service */
                from("direct:start")
                    .to("weather:foo?location=Madrid,Spain&appid=9162755b2efa555823cfe0451d7fff38&geolocationAccessKey=test&geolocationRequestHostIP=test")
                    .to("mock:result");
            }
        };
    }

}
