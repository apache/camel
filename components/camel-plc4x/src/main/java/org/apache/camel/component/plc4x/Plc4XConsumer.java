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
package org.apache.camel.component.plc4x;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.support.DefaultConsumer;
import org.apache.plc4x.java.api.exceptions.PlcConnectionException;
import org.apache.plc4x.java.api.messages.PlcReadRequest;
import org.apache.plc4x.java.scraper.config.JobConfigurationImpl;
import org.apache.plc4x.java.scraper.config.ScraperConfiguration;
import org.apache.plc4x.java.scraper.config.triggeredscraper.ScraperConfigurationTriggeredImpl;
import org.apache.plc4x.java.scraper.exception.ScraperException;
import org.apache.plc4x.java.scraper.triggeredscraper.TriggeredScraperImpl;
import org.apache.plc4x.java.scraper.triggeredscraper.triggerhandler.collector.TriggerCollector;
import org.apache.plc4x.java.scraper.triggeredscraper.triggerhandler.collector.TriggerCollectorImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Plc4XConsumer extends DefaultConsumer {
    private static final Logger LOGGER = LoggerFactory.getLogger(Plc4XConsumer.class);

    private final Map<String, String> tags;
    private final String trigger;
    private final Plc4XEndpoint plc4XEndpoint;

    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> future;

    public Plc4XConsumer(Plc4XEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.plc4XEndpoint = endpoint;
        this.tags = endpoint.getTags();
        this.trigger = endpoint.getTrigger();
    }

    @Override
    public String toString() {
        return "Plc4XConsumer[" + plc4XEndpoint + "]";
    }

    @Override
    public Endpoint getEndpoint() {
        return plc4XEndpoint;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        try {
            plc4XEndpoint.setupConnection();
        } catch (PlcConnectionException e) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.error("Connection setup failed, stopping Consumer", e);
            } else {
                LOGGER.error("Connection setup failed, stopping Consumer");
            }
            doStop();
        }
        if (trigger == null) {
            startUnTriggered();
        } else {
            startTriggered();
        }
    }

    private void startUnTriggered() {
        try {
            plc4XEndpoint.reconnectIfNeeded();
        } catch (PlcConnectionException e) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.warn("Unable to reconnect, skipping request", e);
            } else {
                LOGGER.warn("Unable to reconnect, skipping request");
            }
            return;
        }

        PlcReadRequest request = plc4XEndpoint.buildPlcReadRequest();

        future = executorService.schedule(() -> request.execute().thenAccept(response -> {
            try {
                Exchange exchange = plc4XEndpoint.createExchange();
                Map<String, Object> rsp = new HashMap<>();
                for (String field : response.getFieldNames()) {
                    rsp.put(field, response.getObject(field));
                }
                exchange.getIn().setBody(rsp);
                getProcessor().process(exchange);
            } catch (Exception e) {
                getExceptionHandler().handleException(e);
            }
        }), 500, TimeUnit.MILLISECONDS);
    }

    private void startTriggered() throws ScraperException {
        ScraperConfiguration configuration = getScraperConfig(tags);
        TriggerCollector collector = new TriggerCollectorImpl(plc4XEndpoint.getPlcDriverManager());

        TriggeredScraperImpl scraper = new TriggeredScraperImpl(configuration, (job, alias, response) -> {
            try {
                plc4XEndpoint.reconnectIfNeeded();

                Exchange exchange = plc4XEndpoint.createExchange();
                exchange.getIn().setBody(response);
                getProcessor().process(exchange);
            } catch (PlcConnectionException e) {
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.warn("Unable to reconnect, skipping request", e);
                } else {
                    LOGGER.warn("Unable to reconnect, skipping request");
                }
            } catch (Exception e) {
                getExceptionHandler().handleException(e);
            }
        }, collector);
        scraper.start();
        collector.start();
    }

    private ScraperConfigurationTriggeredImpl getScraperConfig(Map<String, String> tagList) {
        String config = "(TRIGGER_VAR," + plc4XEndpoint.getPeriod() + ",(" + plc4XEndpoint.getTrigger() + ")==(true))";
        List<JobConfigurationImpl> job = Collections.singletonList(
                new JobConfigurationImpl("PLC4X-Camel", config, 0, Collections.singletonList(Constants.PLC_NAME), tagList));
        Map<String, String> source = Collections.singletonMap(Constants.PLC_NAME, plc4XEndpoint.getUri());
        return new ScraperConfigurationTriggeredImpl(source, job);
    }

    @Override
    protected void doStop() throws Exception {
        // First stop the polling process
        if (future != null) {
            future.cancel(true);
        }
        super.doStop();
    }

}
