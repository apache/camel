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

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.bonita.api.model.FileInput;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Counts the JAX-RS clients that a bonita producer creates and closes: one client for all the exchanges of the
 * producer, closed when the producer stops, and the client of each login closed after the login.
 * <p/>
 * The clients are seen through {@link ClientBuilder#newBuilder()}, which the component calls for each client. The
 * static mock only records what the real builder builds; it works on the thread of the test, which is the thread that
 * starts the route and sends the exchanges (direct endpoint, synchronous requests). Without a file input the requests
 * of an exchange are the process lookup and the case creation, each after a login; with a file input the file is
 * uploaded in between, after one more login, through the client of the producer.
 */
class BonitaStartProducerClientTest {

    private static final String LOGIN = "/bonita/loginservice";
    private static final String PROCESS = "/bonita/API/bpm/process";
    private static final String UPLOAD = "/bonita/portal/resource/process/orders/1.0/API/formFileUpload";
    private static final String INSTANTIATION = "/bonita/API/bpm/process/7/instantiation";

    private final List<Client> clients = new ArrayList<>();
    private WireMockServer server;
    private MockedStatic<ClientBuilder> builders;
    private CamelContext context;

    @BeforeEach
    void setup() throws Exception {
        server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
        server.stubFor(post(urlEqualTo(LOGIN))
                .willReturn(aResponse().withHeader("Set-Cookie", "JSESSIONID=session", "X-Bonita-API-Token=token")));
        server.stubFor(get(urlPathEqualTo(PROCESS)).withQueryParam("s", equalTo("orders"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody("[{\"id\":\"7\",\"name\":\"orders\",\"version\":\"1.0\"}]")));
        server.stubFor(post(urlEqualTo(UPLOAD))
                .willReturn(aResponse().withHeader("Content-Type", "text/plain;charset=UTF-8")
                        .withBody("{\"filename\":\"report.txt\",\"tempPath\":\"tmp.doc\","
                                  + "\"contentType\":\"text/plain\"}")));
        server.stubFor(post(urlEqualTo(INSTANTIATION))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("{\"caseId\":\"1\"}")));

        builders = Mockito.mockStatic(ClientBuilder.class, Mockito.CALLS_REAL_METHODS);
        builders.when(ClientBuilder::newBuilder).thenAnswer(invocation -> {
            ClientBuilder builder = Mockito.spy((ClientBuilder) invocation.callRealMethod());
            Mockito.doAnswer(build -> {
                Client client = (Client) build.callRealMethod();
                clients.add(client);
                return client;
            }).when(builder).build();
            return builder;
        });

        context = new DefaultCamelContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("bonita")
                        .to("bonita:startCase?hostname=localhost&port=" + server.port()
                            + "&processName=orders&username=user&password=pass");
            }
        });
        context.start();
    }

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.stop();
        }
        if (builders != null) {
            builders.close();
        }
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void oneClientForAllTheExchangesOfTheProducerClosedOnStop() {
        startCases(3);

        assertEquals(3, server.findAll(anyRequestedFor(urlEqualTo(INSTANTIATION))).size(), "cases started");
        assertEquals(1, openClients(), "clients still open after three exchanges, of " + clients.size() + " created");

        context.stop();

        assertEquals(0, openClients(), "clients still open after the stop, of " + clients.size() + " created");
    }

    @Test
    void fileInputIsUploadedWithTheClientOfTheProducer() {
        startCases(3, true);

        assertEquals(3, server.findAll(anyRequestedFor(urlEqualTo(UPLOAD))).size(), "files uploaded");
        assertEquals(3, server.findAll(anyRequestedFor(urlEqualTo(INSTANTIATION))).size(), "cases started");
        assertEquals(1, openClients(), "clients still open after three exchanges with a file input, of "
                                       + clients.size() + " created");

        context.stop();

        assertEquals(0, openClients(), "clients still open after the stop, of " + clients.size() + " created");
    }

    @Test
    void routeStartedAgainGetsAClientOfItsOwn() throws Exception {
        startCases(1);
        context.getRouteController().stopRoute("bonita");

        assertEquals(0, openClients(), "clients still open after the route stopped, of " + clients.size() + " created");

        context.getRouteController().startRoute("bonita");
        startCases(2);

        assertEquals(3, server.findAll(anyRequestedFor(urlEqualTo(INSTANTIATION))).size(), "cases started");
        assertEquals(1, openClients(), "clients still open after the route started again, of " + clients.size()
                                       + " created");
    }

    private void startCases(int count) {
        startCases(count, false);
    }

    private void startCases(int count, boolean withFile) {
        ProducerTemplate template = context.createProducerTemplate();
        try {
            for (int i = 0; i < count; i++) {
                Exchange exchange = template.send("direct:start", e -> e.getIn().setBody(inputs(withFile)));
                assertNull(exchange.getException(), "exception of the exchange");
            }
        } finally {
            template.stop();
        }
    }

    private static Map<String, Serializable> inputs(boolean withFile) {
        Map<String, Serializable> inputs = new HashMap<>();
        inputs.put("amount", 1);
        if (withFile) {
            inputs.put("document", new FileInput("report.txt", "content".getBytes(StandardCharsets.UTF_8)));
        }
        return inputs;
    }

    private long openClients() {
        return clients.stream().filter(client -> !isClosed(client)).count();
    }

    /**
     * A closed JAX-RS client throws {@link IllegalStateException} from its methods.
     */
    private static boolean isClosed(Client client) {
        try {
            client.getConfiguration();
            return false;
        } catch (IllegalStateException e) {
            return true;
        }
    }
}
