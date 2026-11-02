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
package org.apache.camel.component.bonita.producer;

import java.io.InputStream;
import java.util.Locale;
import java.util.Map;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.component.bonita.BonitaConfiguration;
import org.apache.camel.component.bonita.BonitaConstants;
import org.apache.camel.component.bonita.BonitaEndpoint;
import org.apache.camel.component.bonita.api.BonitaAPI;
import org.apache.camel.component.bonita.api.BonitaAPIBuilder;
import org.apache.camel.component.bonita.api.model.CaseCreationResponse;
import org.apache.camel.component.bonita.api.model.ProcessDefinitionResponse;
import org.apache.camel.component.bonita.api.util.BonitaAPIConfig;

public class BonitaStartProducer extends BonitaProducer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // one JAX-RS client for all the exchanges of this producer, created on start and closed on stop
    private volatile Client client;
    private volatile BonitaAPI bonitaApi;

    public BonitaStartProducer(BonitaEndpoint endpoint, BonitaConfiguration configuration) {
        super(endpoint, configuration);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        // Setup access type (HTTP on local host)
        String hostname = this.configuration.getHostname();
        String port = this.configuration.getPort();
        String username = this.configuration.getUsername();
        String password = this.configuration.getPassword();
        BonitaAPIConfig bonitaAPIConfig = new BonitaAPIConfig(hostname, port, username, password);
        client = ClientBuilder.newBuilder().build();
        bonitaApi = BonitaAPIBuilder.build(bonitaAPIConfig, client);
    }

    @Override
    protected void doStop() throws Exception {
        bonitaApi = null;
        Client toClose = client;
        client = null;
        if (toClose != null) {
            toClose.close();
        }
        super.doStop();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        Map inputs = getInputs(exchange);
        String processName = this.configuration.getProcessName();
        BonitaAPI api = bonitaApi;
        ProcessDefinitionResponse processDefinition = api.getProcessDefinition(processName);
        CaseCreationResponse response = api.startCase(processDefinition, inputs);
        if (response != null) {
            exchange.getMessage().setHeader(BonitaConstants.CASE_ID, response.getCaseId());
        }
    }

    /**
     * The contract inputs of the case: the Map body, or a JSON object given as text (String, byte[] or InputStream)
     * parsed into a Map. Any other body, and JSON text that is not an object, fails with an
     * {@link InvalidPayloadException}, not as a null contract input.
     */
    private static Map getInputs(Exchange exchange) throws InvalidPayloadException {
        Message in = exchange.getIn();
        Object body = in.getBody();
        if (body instanceof String || body instanceof byte[] || body instanceof InputStream) {
            String text = in.getMandatoryBody(String.class);
            JsonNode json;
            try {
                json = MAPPER.readTree(text);
            } catch (JsonProcessingException e) {
                throw new InvalidPayloadException(exchange, Map.class, in, e);
            }
            if (json == null || !json.isObject()) {
                String type
                        = json == null || json.isMissingNode() ? "empty" : json.getNodeType().name().toLowerCase(Locale.ROOT);
                throw new InvalidPayloadException(
                        exchange, Map.class, in,
                        new IllegalArgumentException("The JSON text is not an object of contract inputs but " + type));
            }
            return MAPPER.convertValue(json, Map.class);
        }
        return in.getMandatoryBody(Map.class);
    }

}
