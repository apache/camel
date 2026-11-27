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

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultPollingEndpoint;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;

/**
 * Poll the weather information from Open Weather Map.
 */
@UriEndpoint(firstVersion = "2.12.0", scheme = "weather", title = "Weather", syntax = "weather:name",
             category = { Category.API }, headersClass = WeatherConstants.class)
@BodyTypes(consumerOut = @BodyType(value = Carrier.TEXT, javaType = "java.lang.String", format = "json",
                                   dependsOn = { "mode", "headerName" },
                                   description = "The OpenWeatherMap response as a String: JSON, or XML or HTML with mode."
                                                 + " With headerName the response is in that header and the body is null"),
           producerIn = @BodyType(value = Carrier.ANY,
                                  description = "The body is not read: the query comes from the options, and the"
                                                + " CamelWeatherLocation header overrides the location"),
           producerOut = @BodyType(value = Carrier.TEXT, javaType = "java.lang.String", format = "json",
                                   dependsOn = { "mode", "headerName" },
                                   description = "The OpenWeatherMap response as a String: JSON, or XML or HTML with mode."
                                                 + " With headerName the response is in that header and the body is left"
                                                 + " unchanged"))
public class WeatherEndpoint extends DefaultPollingEndpoint implements EndpointServiceLocation {

    @UriParam(defaultValue = "" + WeatherConsumer.DEFAULT_CONSUMER_DELAY, javaType = "java.time.Duration",
              label = "consumer,scheduler",
              description = "Milliseconds before the next poll.")
    private long delay = WeatherConsumer.DEFAULT_CONSUMER_DELAY;

    @UriParam
    private final WeatherConfiguration configuration;

    private final WeatherQuery weatherQuery;
    private CloseableHttpClient createdHttpClient;

    public WeatherEndpoint(String uri, WeatherComponent component, WeatherConfiguration properties) {
        super(uri, component);
        this.configuration = properties;
        this.weatherQuery = new WeatherQuery(getConfiguration());
        // ScheduledPollConsumer default delay is 500 millis and that is too often for polling the weather, so we
        // override with a new default value. End user can override this value by providing a delay parameter
        setDelay(WeatherConsumer.DEFAULT_CONSUMER_DELAY);
    }

    @Override
    public String getServiceUrl() {
        return "http://api.openweathermap.org/data/2.5";
    }

    @Override
    public String getServiceProtocol() {
        return "http";
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        WeatherConsumer answer = new WeatherConsumer(this, processor);
        configureConsumer(answer);
        return answer;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new WeatherProducer(this, getWeatherQuery().getQuery());
    }

    public WeatherConfiguration getConfiguration() {
        return configuration;
    }

    public WeatherQuery getWeatherQuery() {
        return this.weatherQuery;
    }

    /**
     * Milliseconds before the next poll.
     */
    @Override
    public void setDelay(long delay) {
        super.setDelay(delay);
        this.delay = delay;
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();
        // the producer builds its query, which may call the geolocation provider, when it is created
        createHttpClientIfNotSet();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        // again after a stop, which closed the client the endpoint created
        createHttpClientIfNotSet();
    }

    private void createHttpClientIfNotSet() {
        if (getConfiguration().getHttpClient() == null) {
            createdHttpClient = HttpClients.createDefault();
            getConfiguration().setHttpClient(createdHttpClient);
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        // close only the client that the endpoint created: a client that is set may be shared
        CloseableHttpClient client = createdHttpClient;
        createdHttpClient = null;
        if (client != null) {
            if (getConfiguration().getHttpClient() == client) {
                getConfiguration().setHttpClient(null);
            }
            client.close();
        }
    }

}
