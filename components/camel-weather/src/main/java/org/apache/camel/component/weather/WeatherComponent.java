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

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.util.IOHelper;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

/**
 * A <a href="http://camel.apache.org/weather.html">Weather Component</a>.
 * <p/>
 * Camel uses <a href="http://openweathermap.org/api#weather">Open Weather</a> to get the information.
 */
@Component("weather")
public class WeatherComponent extends DefaultComponent {

    @Metadata(label = "advanced")
    private CloseableHttpClient httpClient;
    private String geolocationAccessKey;
    private String geolocationRequestHostIP;

    public WeatherComponent() {
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        WeatherConfiguration configuration = new WeatherConfiguration(this);
        configuration.setGeolocationAccessKey(geolocationAccessKey);
        configuration.setGeolocationRequestHostIP(geolocationRequestHostIP);

        WeatherEndpoint endpoint = new WeatherEndpoint(uri, this, configuration);
        setProperties(endpoint, parameters);

        if (httpClient == null) {
            httpClient = HttpClients.createDefault();
        }

        return endpoint;
    }

    public CloseableHttpClient getHttpClient() {
        return httpClient;
    }

    /**
     * To use an existing configured http client (for example with http proxy)
     */
    public void setHttpClient(CloseableHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public String getGeolocationAccessKey() {
        return geolocationAccessKey;
    }

    /**
     * The geolocation service now needs an accessKey to be used
     */
    public void setGeolocationAccessKey(String geolocationAccessKey) {
        this.geolocationAccessKey = geolocationAccessKey;
    }

    public String getGeolocationRequestHostIP() {
        return geolocationRequestHostIP;
    }

    /**
     * The geolocation service now needs to specify the IP associated to the accessKey you're using
     */
    public void setGeolocationRequestHostIP(String geolocationRequestHostIP) {
        this.geolocationRequestHostIP = geolocationRequestHostIP;
    }

    @Override
    protected void doShutdown() throws Exception {
        super.doShutdown();

        if (httpClient != null) {
            IOHelper.close(httpClient);
            httpClient = null;
        }
    }
}
