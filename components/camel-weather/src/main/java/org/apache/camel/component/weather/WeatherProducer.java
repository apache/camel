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
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WeatherProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(WeatherProducer.class);

    private final String query;

    public WeatherProducer(WeatherEndpoint endpoint, String query) {
        super(endpoint);
        this.query = query;
    }

    @Override
    public WeatherEndpoint getEndpoint() {
        return (WeatherEndpoint) super.getEndpoint();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        String q = query;
        String location = exchange.getIn().getHeader(WeatherConstants.WEATHER_LOCATION, String.class);
        if (location != null) {
            q = getEndpoint().getConfiguration().getQuery(location);
        }

        HttpClient httpClient = ((WeatherComponent) getEndpoint().getComponent()).getHttpClient();
        HttpGet method = new HttpGet(q);
        try {
            LOG.debug("Going to execute the Weather query {}", q);
            HttpResponse response = httpClient.execute(method);
            if (HttpStatus.SC_OK != response.getStatusLine().getStatusCode()) {
                throw new IllegalStateException("Got the invalid http status value '" + response.getStatusLine().getStatusCode() + "' as the result of the query '" + query + "'");
            }
            String weather = EntityUtils.toString(response.getEntity(), "UTF-8");
            LOG.debug("Got back the Weather information {}", weather);

            if (ObjectHelper.isEmpty(weather)) {
                throw new IllegalStateException("Got the unexpected value '" + weather + "' as the result of the query '" + q + "'");
            }

            String header = getEndpoint().getConfiguration().getHeaderName();
            if (header != null) {
                exchange.getIn().setHeader(header, weather);
            } else {
                exchange.getIn().setBody(weather);
            }
            exchange.getIn().setHeader(WeatherConstants.WEATHER_QUERY, q);
        } finally {
            method.releaseConnection();
        }
    }
}
