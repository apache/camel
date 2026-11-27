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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.bonita.api.BonitaAPI;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which process a bonita producer starts a case of, under which context path, and that it does not start without the
 * options it needs. The Bonita server is a WireMock server.
 */
class BonitaProcessLookupTest {

    private WireMockServer server;
    private CamelContext context;
    private ProducerTemplate template;

    @BeforeEach
    void setup() {
        server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
        for (String base : new String[] { "/bonita", "/bpm/engine" }) {
            server.stubFor(post(urlEqualTo(base + "/loginservice"))
                    .willReturn(aResponse().withHeader("Set-Cookie", "JSESSIONID=session", "X-Bonita-API-Token=token")));
            // any other lookup, such as a search for "orders": a process whose name only contains "orders"
            server.stubFor(get(urlPathEqualTo(base + "/API/bpm/process")).atPriority(10)
                    .willReturn(json("[{\"id\":\"9\",\"name\":\"orders-archive\",\"version\":\"1.0\"}]")));
            // the process named exactly "orders", deployed in two versions
            server.stubFor(get(urlPathEqualTo(base + "/API/bpm/process")).atPriority(5)
                    .withQueryParam("f", equalTo("name=orders"))
                    .willReturn(json("[{\"id\":\"7\",\"name\":\"orders\",\"version\":\"1.0\"},"
                                     + "{\"id\":\"8\",\"name\":\"orders\",\"version\":\"2.0\"}]")));
            server.stubFor(get(urlPathEqualTo(base + "/API/bpm/process")).atPriority(1)
                    .withQueryParam("f", equalTo("name=orders")).withQueryParam("f", equalTo("version=2.0"))
                    .willReturn(json("[{\"id\":\"8\",\"name\":\"orders\",\"version\":\"2.0\"}]")));
            for (String id : new String[] { "7", "8", "9" }) {
                server.stubFor(post(urlEqualTo(base + "/API/bpm/process/" + id + "/instantiation"))
                        .willReturn(json("{\"caseId\":\"" + id + "00\"}")));
            }
        }
        context = new DefaultCamelContext();
    }

    private static ResponseDefinitionBuilder json(String body) {
        return aResponse().withHeader("Content-Type", "application/json").withBody(body);
    }

    @AfterEach
    void tearDown() {
        if (template != null) {
            template.stop();
        }
        context.stop();
        server.stop();
    }

    private void start(String options) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to("bonita:startCase?hostname=localhost&port=" + server.port() + options);
            }
        });
        context.start();
        template = context.createProducerTemplate();
    }

    private String startCase() {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("amount", 1);
        Exchange exchange = template.send("direct:start", e -> e.getIn().setBody(inputs));
        assertNull(exchange.getException(), "exception of the exchange");
        return exchange.getMessage().getHeader(BonitaConstants.CASE_ID, String.class);
    }

    @Test
    void processNameIsMatchedExactly() throws Exception {
        start("&processName=orders&username=user&password=pass");

        assertEquals("700", startCase(), "case of the process named orders, not of orders-archive");
    }

    @Test
    void severalVersionsAreWarnedOnceNotForEveryExchange() throws Exception {
        Logger logger = (Logger) LogManager.getLogger(BonitaAPI.class);
        Level level = logger.getLevel();
        LogEventCaptor captor = new LogEventCaptor();
        Configurator.setLevel(logger.getName(), Level.DEBUG);
        logger.addAppender(captor);
        try {
            start("&processName=orders&username=user&password=pass");

            for (int i = 0; i < 3; i++) {
                assertEquals("700", startCase(), "case of the first version that Bonita returns");
            }
        } finally {
            logger.removeAppender(captor);
            Configurator.setLevel(logger.getName(), level);
        }

        assertEquals(1, captor.messages(Level.WARN).size(), "one WARN for three exchanges: " + captor.messages(Level.WARN));
        assertTrue(captor.messages(Level.WARN).get(0).contains("2 versions of the process orders"),
                captor.messages(Level.WARN).get(0));
        assertEquals(2, captor.messages(Level.DEBUG).size(), "the later lookups log at DEBUG");
    }

    @Test
    void processVersionSelectsTheVersion() throws Exception {
        start("&processName=orders&processVersion=2.0&username=user&password=pass");

        assertEquals("800", startCase(), "case of version 2.0");
    }

    @Test
    void basePathIsTheContextPathOfTheRequests() throws Exception {
        start("&basePath=/bpm/engine/&processName=orders&username=user&password=pass");

        assertEquals("700", startCase());
        assertEquals(1, server.findAll(anyRequestedFor(urlPathEqualTo("/bpm/engine/API/bpm/process"))).size());
        assertTrue(server.findAll(anyRequestedFor(urlEqualTo("/bpm/engine/loginservice"))).size() > 0, "login");
        assertTrue(server.findAll(anyRequestedFor(urlPathEqualTo("/bonita/API/bpm/process"))).isEmpty());
    }

    @Test
    void producerDoesNotStartWithoutProcessName() {
        Exception e = assertThrows(Exception.class, () -> start("&username=user&password=pass"));
        assertCause(e, "processName");
    }

    @Test
    void producerDoesNotStartWithoutPassword() {
        Exception e = assertThrows(Exception.class, () -> start("&processName=orders&username=user"));
        assertCause(e, "password");
        assertTrue(server.getAllServeEvents().isEmpty(), "no request was sent");
    }

    private static void assertCause(Throwable e, String option) {
        Throwable cause = e;
        while (cause.getCause() != null && !(cause instanceof IllegalArgumentException)) {
            cause = cause.getCause();
        }
        assertInstanceOf(IllegalArgumentException.class, cause);
        assertTrue(cause.getMessage().contains(option), cause.getMessage());
    }

    private static final class LogEventCaptor extends AbstractAppender {
        private final List<LogEvent> events = new CopyOnWriteArrayList<>();

        LogEventCaptor() {
            super("BonitaProcessLookupTestCaptor", null, null, false, Property.EMPTY_ARRAY);
            start();
        }

        @Override
        public void append(LogEvent event) {
            events.add(event.toImmutable());
        }

        List<String> messages(Level level) {
            return events.stream()
                    .filter(event -> event.getLevel() == level)
                    .map(event -> event.getMessage().getFormattedMessage())
                    .toList();
        }
    }
}
