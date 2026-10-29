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
package org.apache.camel.component.mtconnect;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;

/**
 * Sends one MTConnect request per exchange and sets the answer as the body: the agent's device model document for
 * probe, its assets document for assets, a list of observation maps for current and sample. The observation maps carry
 * the probe's metadata, so the producer reads the probe on first use and again when a reply carries another instance
 * id. An agent error document fails the exchange with {@link MTConnectErrorException}; the producer has no outage
 * state.
 */
public class MTConnectProducer extends DefaultProducer {

    private final MTConnectEndpoint endpoint;
    private AgentReader reader;
    // an immutable snapshot that concurrent exchanges share; replaced when the agent restarts
    private volatile AgentDocument.Probe probe;

    /** One request to the agent, sent while the exchange is processed. */
    private interface Request {
        AgentReader.Answer send() throws AgentFailure, InterruptedException;
    }

    public MTConnectProducer(MTConnectEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        reader = AgentReader.open(endpoint, this);
    }

    @Override
    protected void doStop() throws Exception {
        if (reader != null) {
            // a request waiting for a silent agent returns at once
            reader.abort();
            reader.close();
            reader = null;
        }
        probe = null;
        super.doStop();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        Message message = exchange.getMessage();
        MTConnectOperation operation
                = message.getHeader(MTConnectConstants.OPERATION, endpoint.getOperation(), MTConnectOperation.class);
        if (operation == null) {
            throw new IllegalArgumentException(
                    "No MTConnect operation: set the operation option or the " + MTConnectConstants.OPERATION
                                               + " header");
        }
        switch (operation) {
            case probe -> probe = (AgentDocument.Probe) document(exchange, reader::probe, AgentDocument.Probe.class);
            case current -> {
                Long at = message.getHeader(MTConnectConstants.AT, endpoint.getAt(), Long.class);
                observations(exchange, () -> reader.current(at), MTConnectConstants.DOCUMENT_CURRENT);
            }
            case sample -> {
                Long from = message.getHeader(MTConnectConstants.FROM, endpoint.getFrom(), Long.class);
                Long to = message.getHeader(MTConnectConstants.TO, endpoint.getTo(), Long.class);
                long requested = message.getHeader(MTConnectConstants.COUNT, endpoint.getCount(), Integer.class);
                Long bufferSize = model(exchange, null).header().bufferSize();
                long count = bufferSize == null ? requested : Math.min(requested, bufferSize);
                observations(exchange, () -> reader.sample(from, to, count), MTConnectConstants.DOCUMENT_SAMPLE);
            }
            case assets -> {
                String ids = message.getHeader(MTConnectConstants.ASSET_ID, String.class);
                String type = message.getHeader(MTConnectConstants.ASSET_TYPE, endpoint.getAssetType(), String.class);
                long count = message.getHeader(MTConnectConstants.COUNT, endpoint.getCount(), Integer.class);
                Request request = ids == null || ids.isBlank()
                        ? () -> reader.assets(type, count, endpoint.isRemoved())
                        : () -> reader.asset(List.of(ids.split(";")));
                document(exchange, request, AgentDocument.Assets.class);
            }
        }
    }

    /** Sets the agent's document as received as the body. */
    private AgentDocument document(Exchange exchange, Request request, Class<? extends AgentDocument> expected)
            throws Exception {
        AgentReader.Answer answer = request(exchange, request, expected);
        Message message = exchange.getMessage();
        message.setBody(new String(answer.body(), StandardCharsets.UTF_8));
        setHeader(message, MTConnectConstants.INSTANCE_ID, answer.document().header().instanceId());
        return answer.document();
    }

    private void observations(Exchange exchange, Request request, String document) throws Exception {
        AgentDocument.Streams streams
                = (AgentDocument.Streams) request(exchange, request, AgentDocument.Streams.class).document();
        DocumentHeader header = streams.header();
        Map<String, DataItem> dataItems = model(exchange, header.instanceId()).dataItems();
        List<Map<String, Object>> body = new ArrayList<>(streams.observations().size());
        for (Observation observation : streams.observations()) {
            body.add(ObservationMessages.map(endpoint.getAgentUrl(), dataItems.get(observation.dataItemId()),
                    observation, header.instanceId(), document));
        }
        Message message = exchange.getMessage();
        message.setBody(body);
        setHeader(message, MTConnectConstants.INSTANCE_ID, header.instanceId());
        setHeader(message, MTConnectConstants.FIRST_SEQUENCE, header.firstSequence());
        setHeader(message, MTConnectConstants.LAST_SEQUENCE, header.lastSequence());
        setHeader(message, MTConnectConstants.NEXT_SEQUENCE, header.nextSequence());
    }

    /** The probe snapshot; read again when there is none yet or the given instance id differs from it. */
    private AgentDocument.Probe model(Exchange exchange, Long instanceId) throws Exception {
        AgentDocument.Probe snapshot = probe;
        if (snapshot == null || instanceId != null && !Objects.equals(instanceId, snapshot.header().instanceId())) {
            snapshot = (AgentDocument.Probe) request(exchange, reader::probe, AgentDocument.Probe.class).document();
            probe = snapshot;
        }
        return snapshot;
    }

    /**
     * Sends a request and checks that the answer is the expected document. An error document fails the exchange with
     * the agent's error codes and texts, a transport failure or another document with its description.
     */
    private AgentReader.Answer request(Exchange exchange, Request request, Class<? extends AgentDocument> expected)
            throws Exception {
        AgentReader.Answer answer;
        try {
            answer = request.send();
            if (answer.document() instanceof AgentDocument.Error error) {
                throw new MTConnectErrorException(endpoint.getAgentUrl(), error);
            }
            answer.expect(expected);
        } catch (AgentFailure failure) {
            throw new CamelExchangeException(
                    "The MTConnect agent " + endpoint.getAgentUrl() + " cannot be read: " + failure.getMessage(), exchange,
                    failure.getCause());
        }
        return answer;
    }

    private static void setHeader(Message message, String name, Object value) {
        if (value != null) {
            message.setHeader(name, value);
        }
    }
}
