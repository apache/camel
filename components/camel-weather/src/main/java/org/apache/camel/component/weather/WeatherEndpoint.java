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
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultPollingEndpoint;

/**
 * Polls the weather information from Open Weather Map.
 */
@UriEndpoint(firstVersion = "2.12.0", scheme = "weather", title = "Weather", syntax = "weather:name", label = "api")
public class WeatherEndpoint extends DefaultPollingEndpoint {

    @UriParam
    private WeatherConfiguration configuration;

    public WeatherEndpoint(String uri, WeatherComponent component, WeatherConfiguration properties) {
        super(uri, component);
        this.configuration = properties;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        WeatherConsumer answer = new WeatherConsumer(this, processor, this.configuration.getQuery());

        // ScheduledPollConsumer default delay is 500 millis and that is too often for polling a feed, so we override
        // with a new default value. End user can override this value by providing a consumer.delay parameter
        answer.setDelay(WeatherConsumer.DEFAULT_CONSUMER_DELAY);
        configureConsumer(answer);
        return answer;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new WeatherProducer(this, configuration.getQuery());
    }

    public WeatherConfiguration getConfiguration() {
        return configuration;
    }
}
