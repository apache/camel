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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.plc4x.java.api.exceptions.PlcConnectionException;
import org.apache.plc4x.java.api.exceptions.PlcException;
import org.apache.plc4x.java.api.exceptions.PlcInvalidTagException;
import org.apache.plc4x.java.api.messages.PlcWriteRequest;
import org.apache.plc4x.java.api.messages.PlcWriteResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Plc4XProducer extends DefaultAsyncProducer {
    protected AtomicInteger openRequests;
    private final Logger log = LoggerFactory.getLogger(Plc4XProducer.class);
    private final Plc4XEndpoint plc4XEndpoint;

    public Plc4XProducer(Plc4XEndpoint endpoint) {
        super(endpoint);
        plc4XEndpoint = endpoint;
        openRequests = new AtomicInteger();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        try {
            plc4XEndpoint.setupConnection();
        } catch (PlcConnectionException e) {
            if (log.isTraceEnabled()) {
                log.error("Connection setup failed, stopping producer", e);
            } else {
                log.error("Connection setup failed, stopping producer");
            }
            doStop();
        }
        if (!plc4XEndpoint.canWrite()) {
            throw new PlcException("This connection (" + plc4XEndpoint.getUri() + ") doesn't support writing.");
        }
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        try {
            plc4XEndpoint.reconnectIfNeeded();
        } catch (PlcConnectionException e) {
            if (log.isTraceEnabled()) {
                log.warn("Unable to reconnect, skipping request", e);
            } else {
                log.warn("Unable to reconnect, skipping request");
            }
            return;
        }

        Message in = exchange.getIn();
        Object body = in.getBody();
        PlcWriteRequest plcWriteRequest;
        if (body instanceof Map) { //Check if we have a Map
            Map<String, Map<String, Object>> tags = (Map<String, Map<String, Object>>) body;
            plcWriteRequest = plc4XEndpoint.buildPlcWriteRequest(tags);
        } else {
            throw new PlcInvalidTagException("The body must contain a Map<String,Map<String,Object>");
        }
        CompletableFuture<? extends PlcWriteResponse> completableFuture = plcWriteRequest.execute();
        int currentlyOpenRequests = openRequests.incrementAndGet();
        try {
            log.debug("Currently open requests including {}:{}", exchange, currentlyOpenRequests);
            Object plcWriteResponse = completableFuture.get(5000, TimeUnit.MILLISECONDS);
            if (exchange.getPattern().isOutCapable()) {
                Message out = exchange.getMessage();
                out.copyFrom(exchange.getIn());
                out.setBody(plcWriteResponse);
            } else {
                in.setBody(plcWriteResponse);
            }
        } finally {
            int openRequestsAfterFinish = openRequests.decrementAndGet();
            log.trace("Open Requests after {}:{}", exchange, openRequestsAfterFinish);
        }
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        try {
            process(exchange);
            Message out = exchange.getMessage();
            out.copyFrom(exchange.getIn());
        } catch (Exception e) {
            exchange.setMessage(null);
            exchange.setException(e);
        }
        callback.done(true);
        return true;
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        int openRequestsAtStop = openRequests.get();
        log.debug("Stopping with {} open requests", openRequestsAtStop);
        if (openRequestsAtStop > 0) {
            log.warn("There are still {} open requests", openRequestsAtStop);
        }
    }

}
