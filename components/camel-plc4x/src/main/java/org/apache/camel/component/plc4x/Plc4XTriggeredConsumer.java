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

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.support.ScheduledPollConsumer;
import org.apache.plc4x.java.api.exceptions.PlcConnectionException;
import org.apache.plc4x.java.api.exceptions.PlcException;
import org.apache.plc4x.java.api.messages.PlcReadResponse;
import org.apache.plc4x.java.api.types.PlcResponseCode;

/**
 * Reads the trigger tag together with the tags every {@code period} milliseconds and emits the tags once on each rising
 * edge of the trigger.
 */
public class Plc4XTriggeredConsumer extends ScheduledPollConsumer {
    private final Plc4XEndpoint plc4XEndpoint;
    private final String trigger;
    private final int period;

    // whether the trigger was true on the last poll; null when unknown, before the first poll or after a failed one
    private Boolean lastTrigger;

    public Plc4XTriggeredConsumer(Plc4XEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.plc4XEndpoint = endpoint;
        this.trigger = endpoint.getTrigger();
        this.period = endpoint.getPeriod();
        setInitialDelay(0);
        setDelay(period);
        setTimeUnit(TimeUnit.MILLISECONDS);
    }

    @Override
    public Plc4XEndpoint getEndpoint() {
        return plc4XEndpoint;
    }

    @Override
    protected void doStart() throws Exception {
        if (period <= 0) {
            throw new IllegalArgumentException(
                    "The period must be a positive number of milliseconds when a trigger is set, but was " + period);
        }
        Map<String, String> tags = plc4XEndpoint.getTags();
        if (tags != null && tags.containsKey(Constants.TRIGGER)) {
            throw new IllegalArgumentException(
                    "The tag name " + Constants.TRIGGER + " is reserved for the trigger when a trigger is set");
        }
        lastTrigger = null;
        // without autoReconnect a connection that cannot be set up fails the start, as no poll would reconnect
        plc4XEndpoint.setupConnection();
        super.doStart();
    }

    @Override
    protected int poll() throws Exception {
        PlcReadResponse response;
        try {
            response = read();
        } catch (Exception e) {
            lastTrigger = null;
            throw e;
        }
        if (!isRunAllowed()) {
            // the consumer stopped while the read was in progress
            return 0;
        }
        boolean triggered = Boolean.TRUE.equals(response.getObject(Constants.TRIGGER));
        boolean risingEdge = triggered && !Boolean.TRUE.equals(lastTrigger);
        lastTrigger = triggered;
        if (!risingEdge) {
            return 0;
        }

        Map<String, Object> tags = Plc4XEndpoint.tagValues(response);
        tags.remove(Constants.TRIGGER);
        Exchange exchange = createExchange(false);
        exchange.getIn().setBody(tags);
        try {
            getProcessor().process(exchange);
        } catch (Exception e) {
            exchange.setException(e);
        } finally {
            if (exchange.getException() != null) {
                getExceptionHandler().handleException("Error processing exchange", exchange, exchange.getException());
            }
            releaseExchange(exchange, false);
        }
        return 1;
    }

    /**
     * Reads the trigger and the tags in one request; fails unless every tag was read.
     */
    private PlcReadResponse read() throws Exception {
        plc4XEndpoint.reconnectIfNeeded();
        if (plc4XEndpoint.connection == null) {
            throw new PlcConnectionException("Cannot establish connection");
        }
        PlcReadResponse response;
        try {
            response = plc4XEndpoint.buildPlcReadRequest(Map.of(Constants.TRIGGER, trigger)).execute().get();
        } catch (ExecutionException e) {
            throw e.getCause() instanceof Exception cause ? cause : e;
        }
        for (String tag : response.getTagNames()) {
            PlcResponseCode code = response.getResponseCode(tag);
            if (code != PlcResponseCode.OK) {
                throw new PlcException("Reading the tag " + tag + " failed with response code " + code);
            }
        }
        return response;
    }

    @Override
    public String toString() {
        return "Plc4XTriggeredConsumer[" + plc4XEndpoint + "]";
    }
}
