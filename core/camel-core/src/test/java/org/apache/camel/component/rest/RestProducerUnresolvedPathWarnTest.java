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
package org.apache.camel.component.rest;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.log.ConsumingAppender;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Appender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A path parameter with no value leaves its {name} in the uri and the service answers 404 for it, so the producer says
 * which parameter it was. The request is still sent, as it was before (CAMEL-24986).
 */
public class RestProducerUnresolvedPathWarnTest {

    private final List<String> warnings = new CopyOnWriteArrayList<>();
    private final RestComponent restComponent;
    private Appender appender;

    public RestProducerUnresolvedPathWarnTest() {
        DefaultCamelContext context = new DefaultCamelContext();
        context.addComponent("mock-rest", new RestEndpointTest.MockRest());
        restComponent = new RestComponent();
        restComponent.setCamelContext(context);
    }

    @BeforeEach
    public void before() {
        appender = ConsumingAppender.newAppender(
                RestProducer.class.getName(), "UnresolvedPath", Level.WARN,
                event -> warnings.add(event.getMessage().getFormattedMessage()));
    }

    @AfterEach
    public void after() {
        if (appender != null) {
            appender.stop();
        }
    }

    private RestProducer createProducer(String uri) throws Exception {
        final RestEndpoint restEndpoint = (RestEndpoint) restComponent.createEndpoint(uri);
        restEndpoint.setConsumerComponentName("mock-rest");
        restEndpoint.setParameters(new HashMap<>());
        restEndpoint.setHost("http://localhost");
        restEndpoint.setBindingMode("json");
        return (RestProducer) restEndpoint.createProducer();
    }

    @Test
    public void testSaysWhichParameterHasNoValue() throws Exception {
        RestProducer producer = createProducer("rest:get:list/{id}/{val}");
        Exchange exchange = producer.createExchange();
        Message message = exchange.getIn();
        message.setHeader("id", 1);

        producer.process(exchange);

        // the request is still sent, with the placeholder in it, as before
        assertEquals("http://localhost/list/1/{val}", message.getHeader(Exchange.REST_HTTP_URI));
        assertEquals(1, warnings.size(), warnings.toString());
        assertTrue(warnings.get(0).contains("{val}"), warnings.get(0));
        assertTrue(warnings.get(0).contains("set the header val"), warnings.get(0));
    }

    @Test
    public void testSaysItOncePerParameter() throws Exception {
        RestProducer producer = createProducer("rest:get:list/{id}");

        for (int i = 0; i < 3; i++) {
            Exchange exchange = producer.createExchange();
            producer.process(exchange);
        }

        // a route that is wrong is wrong for every message, so it is said once
        assertEquals(1, warnings.size(), warnings.toString());
        assertTrue(warnings.get(0).contains("{id}"), warnings.get(0));
    }

    @Test
    public void testSaysNothingWhenEveryParameterHasAValue() throws Exception {
        RestProducer producer = createProducer("rest:get:list/{id}");
        Exchange exchange = producer.createExchange();
        exchange.getIn().setHeader("id", 1);

        producer.process(exchange);

        assertEquals("http://localhost/list/1", exchange.getIn().getHeader(Exchange.REST_HTTP_URI));
        assertTrue(warnings.isEmpty(), warnings.toString());
    }
}
