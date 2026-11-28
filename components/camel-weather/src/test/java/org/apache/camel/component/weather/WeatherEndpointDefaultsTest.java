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

import org.apache.camel.Consumer;
import org.apache.camel.support.ScheduledPollConsumer;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * The poll delay of the consumer, the http client of the endpoint, and the forecast queries of weatherApi, without
 * calling OpenWeatherMap.
 */
class WeatherEndpointDefaultsTest extends CamelTestSupport {

    private static final String URI = "weather:foo?location=Madrid,Spain&appid=x";

    private long consumerDelay(String uri) throws Exception {
        Consumer consumer = context.getEndpoint(uri).createConsumer(exchange -> {
        });
        return ((ScheduledPollConsumer) consumer).getDelay();
    }

    @Test
    void consumerPollsOnceAnHourByDefault() throws Exception {
        // the delay of the endpoint was not set, so the consumer got the default of 500 ms
        assertEquals(WeatherConsumer.DEFAULT_CONSUMER_DELAY, consumerDelay(URI));
    }

    @Test
    void delayOptionSetsTheConsumerDelay() throws Exception {
        assertEquals(2000, consumerDelay(URI + "&delay=2000"));
    }

    @Test
    void httpClientThatIsSetIsNotClosed() throws Exception {
        CloseableHttpClient shared = mock(CloseableHttpClient.class);
        context.getRegistry().bind("sharedClient", shared);
        WeatherEndpoint endpoint = context.getEndpoint(URI + "&httpClient=#sharedClient", WeatherEndpoint.class);
        endpoint.start();

        endpoint.stop();

        // the endpoint closed the client of its user, which other code may still use
        verify(shared, never()).close();
        assertSame(shared, endpoint.getConfiguration().getHttpClient());
    }

    @Test
    void endpointStartedAgainHasAnOpenHttpClient() throws Exception {
        WeatherEndpoint endpoint = context.getEndpoint(URI, WeatherEndpoint.class);
        endpoint.start();
        CloseableHttpClient first = endpoint.getConfiguration().getHttpClient();
        assertNotNull(first);

        endpoint.stop();
        endpoint.start();

        // the client was created once and closed on stop, so a restarted endpoint used a closed client
        assertNotNull(endpoint.getConfiguration().getHttpClient());
        assertNotSame(first, endpoint.getConfiguration().getHttpClient());
    }

    private static String query(WeatherApi api, String ids) throws Exception {
        WeatherConfiguration configuration = new WeatherConfiguration();
        configuration.setLocation("Madrid,Spain");
        configuration.setWeatherApi(api);
        if (ids != null) {
            configuration.setIds(ids);
        }
        return new WeatherQuery(configuration).getQuery();
    }

    @Test
    void hourlyWithoutPeriodQueriesTheHourlyForecast() throws Exception {
        String query = query(WeatherApi.Hourly, null);

        // the current weather was queried
        assertTrue(query.startsWith("http://api.openweathermap.org/data/2.5/forecast?q="), query);
        assertTrue(query(WeatherApi.Hourly, "524901").startsWith("http://api.openweathermap.org/data/2.5/forecast?id="));
    }

    @Test
    void dailyWithoutPeriodQueriesTheDailyForecast() throws Exception {
        assertTrue(query(WeatherApi.Daily, null).startsWith("http://api.openweathermap.org/data/2.5/forecast/daily?q="));
        assertTrue(query(WeatherApi.Daily, "524901").startsWith("http://api.openweathermap.org/data/2.5/forecast/daily?id="));
    }

    @Test
    void currentWithoutPeriodQueriesTheCurrentWeather() throws Exception {
        assertTrue(query(WeatherApi.Current, null).startsWith("http://api.openweathermap.org/data/2.5/weather?q="));
        assertTrue(query(null, null).startsWith("http://api.openweathermap.org/data/2.5/weather?q="));
    }
}
