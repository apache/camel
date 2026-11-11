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
package org.apache.camel.component.bonita.api;

import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.apache.camel.component.bonita.api.model.CaseCreationResponse;
import org.apache.camel.component.bonita.api.model.ProcessDefinitionResponse;
import org.apache.camel.component.bonita.api.util.BonitaAPIConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Reads and writes JSON through the provider of the JAX-RS client, against a local server.
 */
public class BonitaAPIJsonTest {

    private WireMockServer server;
    private BonitaAPI api;

    @BeforeEach
    public void setup() {
        server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
        server.stubFor(post(urlEqualTo("/bonita/loginservice")).willReturn(aResponse()
                .withHeader("Set-Cookie", "JSESSIONID=1")
                .withHeader("Set-Cookie", "X-Bonita-API-Token=token")));
        api = BonitaAPIBuilder.build(new BonitaAPIConfig("localhost", String.valueOf(server.port()), "user", "password"));
    }

    @AfterEach
    public void tearDown() {
        server.stop();
    }

    @Test
    public void readsTheProcessDefinition() {
        server.stubFor(get(urlPathEqualTo("/bonita/API/bpm/process")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("[{\"id\":\"1\",\"name\":\"process\"}]")));

        ProcessDefinitionResponse definition = api.getProcessDefinition("process");

        assertEquals("1", definition.getId());
        assertEquals("process", definition.getName());
    }

    @Test
    public void writesDatesAsNumbers() throws Exception {
        server.stubFor(post(urlEqualTo("/bonita/API/bpm/process/1/instantiation")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"caseId\":\"5\"}")));
        ProcessDefinitionResponse definition = new ProcessDefinitionResponse();
        definition.setId("1");
        Map<String, Serializable> inputs = new HashMap<>();
        inputs.put("when", new Date(1000L));

        CaseCreationResponse response = api.startCase(definition, inputs);

        assertEquals("5", response.getCaseId());
        server.verify(postRequestedFor(urlEqualTo("/bonita/API/bpm/process/1/instantiation"))
                .withRequestBody(equalToJson("{\"when\":1000}")));
    }
}
