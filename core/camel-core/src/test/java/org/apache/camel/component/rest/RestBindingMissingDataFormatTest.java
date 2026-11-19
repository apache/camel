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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.log.ConsumingAppender;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.support.DefaultRegistry;
import org.apache.camel.support.processor.RestBindingAdviceFactory;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Appender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A binding mode that needs a data format that cannot be found fails when the route is created, and says which artifact
 * to add; binding mode auto that finds no data format at all says so. The test class path of camel-core has a stand-in
 * jackson data format, so a missing one is a name that does not resolve: gson is a Camel data format that is not on it,
 * noSuchJson is not a Camel data format at all.
 */
public class RestBindingMissingDataFormatTest {

    private final List<String> warnings = new CopyOnWriteArrayList<>();
    private Appender appender;
    private CamelContext context;

    @BeforeEach
    public void before() {
        // a name of its own, so two of these cannot replace each other's appender
        appender = ConsumingAppender.newAppender(
                RestBindingAdviceFactory.class.getName(), "MissingDataFormat-" + System.nanoTime(), Level.WARN,
                event -> warnings.add(event.getMessage().getFormattedMessage()));
        DefaultRegistry registry = new DefaultRegistry();
        registry.bind("dummy-rest", new DummyRestConsumerFactory());
        context = new DefaultCamelContext(registry);
    }

    @AfterEach
    public void after() {
        context.stop();
        if (appender != null) {
            appender.stop();
        }
    }

    @Test
    public void jsonBindingWithoutTheJsonDataFormatFailsAtStartup() throws Exception {
        addRest(RestBindingMode.json, "gson", "/j");

        String message = startupFailure();
        assertTrue(message.startsWith("JSON DataFormat gson not found"), message);
        assertTrue(message.contains("the gson data format is in camel-gson; add camel-gson to the classpath"), message);
    }

    @Test
    public void jsonBindingWithAnUnknownJsonDataFormatFailsAtStartup() throws Exception {
        addRest(RestBindingMode.json, "noSuchJson", "/j");

        String message = startupFailure();
        assertTrue(message.startsWith("JSON DataFormat noSuchJson not found"), message);
        assertTrue(message.contains("not a built-in Camel data format"), message);
    }

    @Test
    public void jsonXmlBindingWithoutTheJsonDataFormatFailsAtStartup() throws Exception {
        addRest(RestBindingMode.json_xml, "noSuchJson", "/j");

        String message = startupFailure();
        assertTrue(message.startsWith("JSON DataFormat noSuchJson not found"), message);
    }

    @Test
    public void xmlBindingWithoutJaxbSaysWhichArtifactToAdd() throws Exception {
        addRest(RestBindingMode.xml, null, "/x");

        String message = startupFailure();
        assertTrue(message.startsWith("XML DataFormat jaxb not found"), message);
        assertTrue(message.contains("add camel-jaxb to the classpath"), message);
    }

    @Test
    public void producerJsonBindingWithoutTheJsonDataFormatSaysWhichArtifactToAdd() throws Exception {
        context.addComponent("mock-rest", new RestEndpointTest.MockRest());
        context.getRestConfiguration().setJsonDataFormat("gson");
        context.start();
        RestEndpoint endpoint = context.getEndpoint(
                "rest:get:hello?host=http://localhost&producerComponentName=mock-rest&bindingMode=json", RestEndpoint.class);
        Producer producer = endpoint.createProducer();

        String message = rootCause(assertThrows(Exception.class, () -> ServiceHelper.initService(producer))).getMessage();
        assertTrue(message.startsWith("JSON DataFormat gson not found"), message);
        assertTrue(message.contains("add camel-gson to the classpath"), message);
    }

    @Test
    public void autoBindingWithNoDataFormatWarnsAndPassesTheBodyThrough() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                restConfiguration().host("localhost").bindingMode(RestBindingMode.auto).jsonDataFormat("noSuchJson");
                rest("/p").post().type(Pojo.class).to("mock:p");
            }
        });
        context.start();

        assertEquals(1, warnings.size(), warnings.toString());
        String warning = warnings.get(0);
        assertTrue(warning.contains("bindingMode auto"), warning);
        assertTrue(warning.contains("noSuchJson"), warning);
        assertTrue(warning.contains("jaxb (camel-jaxb)"), warning);

        // as before: the body is not bound
        MockEndpoint mock = context.getEndpoint("mock:p", MockEndpoint.class);
        mock.expectedMessageCount(1);
        Exchange out = context.createProducerTemplate().request("seda:post-p", e -> {
            e.getIn().setHeader(Exchange.CONTENT_TYPE, "application/json");
            e.getIn().setBody("{\"a\":1}");
        });
        mock.assertIsSatisfied();
        assertNull(out.getException());
        assertInstanceOf(String.class, mock.getReceivedExchanges().get(0).getIn().getBody());
    }

    @Test
    public void autoBindingWithAJsonDataFormatDoesNotWarn() throws Exception {
        // the stand-in jackson is found, jaxb is not: auto binds what it can, as documented
        addRest(RestBindingMode.auto, null, "/p");
        context.start();

        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    public void jsonBindingWithTheJsonDataFormatStarts() throws Exception {
        addRest(RestBindingMode.json, null, "/j");
        context.start();

        assertTrue(context.getStatus().isStarted());
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    public void bindingOffWithClientRequestValidationStartsWithoutAJsonDataFormat() throws Exception {
        // the JSON data format is optional there: it is only used to check that a JSON body parses
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                restConfiguration().host("localhost").bindingMode(RestBindingMode.off).jsonDataFormat("noSuchJson")
                        .clientRequestValidation(true);
                rest("/v").post().to("mock:v");
            }
        });
        context.start();

        assertTrue(context.getStatus().isStarted());
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    private void addRest(RestBindingMode mode, String jsonDataFormat, String path) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                restConfiguration().host("localhost").bindingMode(mode).jsonDataFormat(jsonDataFormat);
                rest(path).post().to("mock:result");
            }
        });
    }

    private String startupFailure() {
        Exception e = assertThrows(Exception.class, context::start);
        IllegalArgumentException iae = assertInstanceOf(IllegalArgumentException.class, rootCause(e));
        return iae.getMessage();
    }

    private static Throwable rootCause(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root;
    }

    public static class Pojo {
        private int a;

        public int getA() {
            return a;
        }

        public void setA(int a) {
            this.a = a;
        }
    }
}
