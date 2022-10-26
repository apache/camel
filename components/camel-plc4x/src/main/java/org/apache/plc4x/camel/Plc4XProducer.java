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
package org.apache.plc4x.camel;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.plc4x.java.api.PlcConnection;
import org.apache.plc4x.java.api.exceptions.PlcException;
import org.apache.plc4x.java.api.exceptions.PlcInvalidFieldException;
import org.apache.plc4x.java.api.messages.PlcWriteRequest;
import org.apache.plc4x.java.api.messages.PlcWriteResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public class Plc4XProducer extends DefaultAsyncProducer {
    private final Logger log = LoggerFactory.getLogger(Plc4XProducer.class);
    private PlcConnection plcConnection;
    private AtomicInteger openRequests;

    public Plc4XProducer(Plc4XEndpoint endpoint) throws PlcException {
        super(endpoint);
        String plc4xURI = endpoint.getEndpointUri().replaceFirst("plc4x:/?/?", "");
        this.plcConnection = endpoint.getConnection();
        if (!plcConnection.getMetadata().canWrite()) {
            throw new PlcException("This connection (" + plc4xURI + ") doesn't support writing.");
        }
        openRequests = new AtomicInteger();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        Message in = exchange.getIn();
        Object body = in.getBody();
        PlcWriteRequest.Builder builder = plcConnection.writeRequestBuilder();
        if (body instanceof Map) { //Check if we have a Map
            Map<String, Map<String, Object>> tags = (Map<String, Map<String, Object>>) body;
            for (Map.Entry<String, Map<String, Object>> entry : tags.entrySet()) {
                //Tags are stored like this --> Map<Tagname,Map<Query,Value>> for writing
                String name = entry.getKey();
                String query = entry.getValue().keySet().iterator().next();
                Object value = entry.getValue().get(query);
                builder.addItem(name,query,value);
            }
        } else {
            throw new PlcInvalidFieldException("The body must contain a Map<String,Map<String,Object>");
        }

        CompletableFuture<? extends PlcWriteResponse> completableFuture = builder.build().execute();
        int currentlyOpenRequests = openRequests.incrementAndGet();
        try {
            log.debug("Currently open requests including {}:{}", exchange, currentlyOpenRequests);
            Object plcWriteResponse = completableFuture.get();
            if (exchange.getPattern().isOutCapable()) {
                Message out = exchange.getOut();
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
            Message out = exchange.getOut();
            out.copyFrom(exchange.getIn());
        } catch (Exception e) {
            exchange.setOut(null);
            exchange.setException(e);
        }
        callback.done(true);
        return true;
    }

    @Override
    protected void doStop() throws Exception {
        int openRequestsAtStop = openRequests.get();
        log.debug("Stopping with {} open requests", openRequestsAtStop);
        if (openRequestsAtStop > 0) {
            log.warn("There are still {} open requests", openRequestsAtStop);
        }
    }

}
