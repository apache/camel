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
package org.apache.camel.component.bonita;

import java.io.ByteArrayInputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Starts cases on a Bonita server (WireMock) and checks what the exchange carries afterwards: the id of the started
 * case, the contract inputs sent from a Map or JSON text body, or the failure of a body that is neither.
 */
class BonitaStartProducerTest {

    private static final String LOGIN = "/bonita/loginservice";
    private static final String PROCESS = "/bonita/API/bpm/process";
    private static final String INSTANTIATION = "/bonita/API/bpm/process/7/instantiation";

    private WireMockServer server;
    private CamelContext context;
    private ProducerTemplate template;

    @BeforeEach
    void setup() throws Exception {
        server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
        server.stubFor(post(urlEqualTo(LOGIN))
                .willReturn(aResponse().withHeader("Set-Cookie", "JSESSIONID=session", "X-Bonita-API-Token=token")));
        server.stubFor(get(urlPathEqualTo(PROCESS)).withQueryParam("s", equalTo("orders"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody("[{\"id\":\"7\",\"name\":\"orders\",\"version\":\"1.0\"}]")));
        server.stubFor(post(urlEqualTo(INSTANTIATION))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("{\"caseId\":\"4021\"}")));

        context = new DefaultCamelContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("bonita:startCase?hostname=localhost&port=" + server.port()
                            + "&processName=orders&username=user&password=pass");
            }
        });
        context.start();
        template = context.createProducerTemplate();
    }

    @AfterEach
    void tearDown() {
        if (template != null) {
            template.stop();
        }
        if (context != null) {
            context.stop();
        }
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void caseIdOfTheStartedCaseIsInAHeader() {
        Map<String, Serializable> inputs = new HashMap<>();
        inputs.put("amount", 1);

        Exchange exchange = template.send("direct:start", e -> e.getIn().setBody(inputs));

        assertNull(exchange.getException(), "exception of the exchange");
        assertEquals("4021", exchange.getMessage().getHeader(BonitaConstants.CASE_ID), "case id header");
        assertSame(inputs, exchange.getMessage().getBody(), "body after the case started");
        assertEquals(1, server.findAll(anyRequestedFor(urlEqualTo(INSTANTIATION))).size(), "cases started");
    }

    @Test
    void jsonTextBodyIsParsedIntoTheContractInputs() {
        String json = "{\"amount\":1,\"customer\":{\"name\":\"Jane\"},\"items\":[\"a\",\"b\"]}";

        Exchange exchange = template.send("direct:start", e -> e.getIn().setBody(json));

        assertCaseStartedWith(exchange, json);
        assertEquals(json, exchange.getMessage().getBody(), "body after the case started");
    }

    @Test
    void jsonBytesBodyIsParsedIntoTheContractInputs() {
        String json = "{\"amount\":2}";

        Exchange exchange = template.send("direct:start",
                e -> e.getIn().setBody(json.getBytes(StandardCharsets.UTF_8)));

        assertCaseStartedWith(exchange, json);
    }

    @Test
    void jsonStreamBodyIsParsedIntoTheContractInputs() {
        String json = "{\"amount\":3}";

        Exchange exchange = template.send("direct:start",
                e -> e.getIn().setBody(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))));

        assertCaseStartedWith(exchange, json);
    }

    @Test
    void jsonArrayTextFailsAsNotAnObject() {
        Exchange exchange = template.send("direct:start", e -> e.getIn().setBody("[{\"amount\":1}]"));

        InvalidPayloadException cause = assertNoCaseStarted(exchange);
        assertTrue(cause.getCause().getMessage().contains("not an object of contract inputs but array"),
                "cause names the JSON type: " + cause.getCause().getMessage());
    }

    @Test
    void jsonNullTextFailsAsNotAnObject() {
        Exchange exchange = template.send("direct:start", e -> e.getIn().setBody("null"));

        InvalidPayloadException cause = assertNoCaseStarted(exchange);
        assertTrue(cause.getCause().getMessage().contains("not an object of contract inputs but null"),
                "cause names the JSON type: " + cause.getCause().getMessage());
    }

    @Test
    void emptyTextFailsAsNotAnObject() {
        Exchange exchange = template.send("direct:start", e -> e.getIn().setBody(""));

        InvalidPayloadException cause = assertNoCaseStarted(exchange);
        assertTrue(cause.getCause().getMessage().contains("not an object of contract inputs but empty"),
                "cause names the JSON type: " + cause.getCause().getMessage());
    }

    @Test
    void textThatIsNotJsonFailsWithTheParseError() {
        Exchange exchange = template.send("direct:start", e -> e.getIn().setBody("amount=1"));

        InvalidPayloadException cause = assertNoCaseStarted(exchange);
        assertInstanceOf(JacksonException.class, cause.getCause(), "cause of the failure");
    }

    @Test
    void otherBodyFailsWithItsType() {
        Exchange exchange = template.send("direct:start", e -> e.getIn().setBody(42));

        InvalidPayloadException cause = assertNoCaseStarted(exchange);
        assertTrue(cause.getMessage().contains("java.lang.Integer"), "message names the body type: " + cause.getMessage());
    }

    @Test
    void missingBodyFailsAsNoBody() {
        Exchange exchange = template.send("direct:start", e -> e.getIn().setBody(null));

        assertNoCaseStarted(exchange);
    }

    private void assertCaseStartedWith(Exchange exchange, String json) {
        assertNull(exchange.getException(), "exception of the exchange");
        assertEquals("4021", exchange.getMessage().getHeader(BonitaConstants.CASE_ID), "case id header");
        assertEquals(1, server.findAll(postRequestedFor(urlEqualTo(INSTANTIATION)).withRequestBody(equalToJson(json)))
                .size(), "cases started with the contract inputs " + json);
    }

    private InvalidPayloadException assertNoCaseStarted(Exchange exchange) {
        InvalidPayloadException cause = assertInstanceOf(InvalidPayloadException.class, exchange.getException());
        assertEquals(Map.class, cause.getType(), "required body type");
        assertNull(exchange.getMessage().getHeader(BonitaConstants.CASE_ID), "case id header");
        assertEquals(0, server.findAll(anyRequestedFor(urlEqualTo(INSTANTIATION))).size(), "cases started");
        return cause;
    }
}
