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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.support.LoggingExceptionHandler;
import org.apache.camel.support.ScheduledPollConsumer;
import org.apache.plc4x.java.api.exceptions.PlcException;
import org.apache.plc4x.java.api.messages.PlcReadResponse;
import org.apache.plc4x.java.api.types.PlcResponseCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads the trigger tag together with the tags every {@code period} milliseconds and emits the tags once on each rising
 * edge of the trigger.
 */
public class Plc4XTriggeredConsumer extends ScheduledPollConsumer {
    private static final Logger LOGGER = LoggerFactory.getLogger(Plc4XTriggeredConsumer.class);

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
        // the endpoint replaces it when an exceptionHandler or bridgeErrorHandler is configured
        setExceptionHandler(new FailedPollExceptionHandler(endpoint.getCamelContext()));
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
        if (getErrorCounter() > 0) {
            // the error counter is reset after this poll, so it still counts the failed polls before it
            LOGGER.info("Polling endpoint: {} succeeded again after {} failed polls", getEndpoint(), getErrorCounter());
        }
        boolean triggered = (Boolean) response.getObject(Constants.TRIGGER);
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
     * Reads the trigger and the tags in one request; fails unless every tag was read and the trigger is a boolean.
     */
    private PlcReadResponse read() throws Exception {
        plc4XEndpoint.reconnectIfNeeded();
        CompletableFuture<? extends PlcReadResponse> future
                = plc4XEndpoint.buildPlcReadRequest(Map.of(Constants.TRIGGER, trigger)).execute();
        PlcReadResponse response;
        try {
            response = future.get(Plc4XEndpoint.REQUEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            throw e.getCause() instanceof Exception cause ? cause : e;
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new TimeoutException(
                    "No response to the read of the trigger " + trigger + " within "
                                       + Plc4XEndpoint.REQUEST_TIMEOUT_MILLIS + " millis");
        }
        for (String tag : response.getTagNames()) {
            PlcResponseCode code = response.getResponseCode(tag);
            if (code != PlcResponseCode.OK) {
                throw new PlcException("Reading the tag " + tag + " failed with response code " + code);
            }
        }
        Object value = response.getObject(Constants.TRIGGER);
        if (!(value instanceof Boolean)) {
            throw new PlcException(
                    "The trigger " + trigger + " must read as a Boolean, but read "
                                   + (value == null ? "no value" : "a " + value.getClass().getName()));
        }
        return response;
    }

    @Override
    public String toString() {
        return "Plc4XTriggeredConsumer[" + plc4XEndpoint + "]";
    }

    /**
     * Logs like the default exception handler of a consumer, but a failed poll only at WARN with the stack trace when
     * it starts a run of failed polls, and the following failed polls of the run at DEBUG without it, so that a PLC
     * that cannot be reached is not logged on every poll.
     */
    private final class FailedPollExceptionHandler extends LoggingExceptionHandler {
        FailedPollExceptionHandler(CamelContext camelContext) {
            super(camelContext, Plc4XTriggeredConsumer.class);
        }

        @Override
        public void handleException(String message, Throwable exception) {
            // a failed poll is reported without an exchange and before it is counted, so the first one of a run finds an
            // error counter of 0
            if (getErrorCounter() == 0) {
                super.handleException(message, exception);
            } else {
                // the first failed poll of the run is logged with the stack trace already
                LOGGER.debug("{}. Caused by: {}", message, exception.getMessage());
            }
        }
    }
}
