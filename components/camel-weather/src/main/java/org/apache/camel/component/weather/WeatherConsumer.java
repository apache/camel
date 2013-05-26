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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.ScheduledPollConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WeatherConsumer extends ScheduledPollConsumer {
    private static final transient Logger LOG = LoggerFactory.getLogger(WeatherConsumer.class);
    public static final long DEFAULT_CONSUMER_DELAY = 60 * 1000L;
    private final WeatherEndpoint endpoint;
    private String query;


    public WeatherConsumer(WeatherEndpoint endpoint, Processor processor, String query) {
        super(endpoint, processor);
        this.endpoint = endpoint;
        this.query = query;
    }

    protected int poll() throws Exception {
        LOG.debug("Executing Weather Query " + this.query);
        URL url = new URL(this.query);
        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
        BufferedReader in = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
        String inputLine;
        String answer = "";
        while ((inputLine = in.readLine()) != null) {
            answer += inputLine;
        }
        in.close();
        urlConnection.disconnect();
        LOG.debug("result = " + answer);
        if (answer != null && !answer.isEmpty()) {
            Exchange exchange = endpoint.createExchange();
            exchange.getIn().setBody(answer);
            exchange.setProperty("query", this.query);
            getProcessor().process(exchange);
            return 1;
        } else {
            return 0;
        }
    }
}