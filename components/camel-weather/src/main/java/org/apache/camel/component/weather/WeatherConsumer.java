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
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.support.ScheduledPollConsumer;
import org.apache.camel.util.ObjectHelper;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WeatherConsumer extends ScheduledPollConsumer {
    public static final long DEFAULT_CONSUMER_DELAY = 60 * 60 * 1000L;

    private static final Logger LOG = LoggerFactory.getLogger(WeatherConsumer.class);

    private String query;

    public WeatherConsumer(WeatherEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    @Override
    public WeatherEndpoint getEndpoint() {
        return (WeatherEndpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        this.query = this.getEndpoint().getWeatherQuery().getQuery();
    }

    @Override
    protected int poll() throws Exception {
        LOG.debug("Going to execute the Weather query {}", query);
        HttpClient httpClient = getEndpoint().getConfiguration().getHttpClient();
        HttpGet getMethod = new HttpGet(query);
        try {
            return httpClient.execute(
                    getMethod,
                    response -> {
                        try {
                            if (HttpStatus.SC_OK != response.getCode()) {
                                LOG.warn("HTTP call for weather returned error status code {} - {} as a result with query: {}",
                                        status,
                                        response.getCode(), query);
                                return 0;
                            }
                            String weather = EntityUtils.toString(response.getEntity(), "UTF-8");
                            LOG.debug("Got back the Weather information {}", weather);
                            if (ObjectHelper.isEmpty(weather)) {
                                // empty response
                                return 0;
                            }

                            Exchange exchange = getEndpoint().createExchange();
                            String header = getEndpoint().getConfiguration().getHeaderName();
                            if (header != null) {
                                exchange.getIn().setHeader(header, weather);
                            } else {
                                exchange.getIn().setBody(weather);
                            }
                            exchange.getIn().setHeader(WeatherConstants.WEATHER_QUERY, query);

                            try {
                                getProcessor().process(exchange);
                            } catch (Exception e) {
                                throw new RuntimeCamelException(e);
                            }

                            return 1;
                        } finally {
                            getMethod.reset();
                        }
                    });
        } catch (RuntimeCamelException e) {
            if (e.getCause() instanceof Exception ex) {
                throw ex;
            }
            throw e;
        }
    }

}
