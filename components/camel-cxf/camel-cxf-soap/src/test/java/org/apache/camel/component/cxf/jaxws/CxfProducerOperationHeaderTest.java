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
package org.apache.camel.component.cxf.jaxws;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.cxf.BusFactory;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.helpers.CastUtils;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.jaxws.EndpointImpl;
import org.apache.cxf.jaxws.JaxWsServerFactoryBean;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;
import org.apache.cxf.service.model.BindingOperationInfo;
import org.apache.hello_world_soap_http.GreeterImpl;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How the producer selects the operation to invoke from the CamelCxfOperationName and CamelCxfOperationNamespace
 * headers, and what it reports when no header or an unknown operation selects it.
 */
public class CxfProducerOperationHeaderTest extends CamelTestSupport {
    private static final String NS = "http://apache.org/hello_world_soap_http";
    private static final List<String> GREETER_OPERATIONS = List.of("sayHi", "greetMe", "greetMeSometime", "greetMeLater");

    private static AvailablePortFinder.Port greeterPort;
    private static AvailablePortFinder.Port echoPort;

    private final List<Map<String, List<String>>> serverHeaders = new CopyOnWriteArrayList<>();
    private final List<LogEvent> producerLogEvents = new CopyOnWriteArrayList<>();
    private EndpointImpl greeterServer;
    private Server echoServer;
    private AbstractAppender captor;
    private Logger producerLogger;

    @BeforeAll
    static void allocatePorts() {
        greeterPort = AvailablePortFinder.find();
        echoPort = AvailablePortFinder.find();
    }

    @AfterAll
    static void releasePorts() {
        if (greeterPort != null) {
            greeterPort.release();
        }
        if (echoPort != null) {
            echoPort.release();
        }
    }

    @BeforeEach
    public void startServers() {
        greeterServer = new EndpointImpl(BusFactory.getDefaultBus(), new GreeterImpl());
        greeterServer.getInInterceptors().add(new AbstractPhaseInterceptor<Message>(Phase.RECEIVE) {
            @Override
            public void handleMessage(Message message) throws Fault {
                Map<String, List<String>> received = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
                Map<String, List<String>> headers = CastUtils.cast((Map<?, ?>) message.get(Message.PROTOCOL_HEADERS));
                if (headers != null) {
                    received.putAll(headers);
                }
                serverHeaders.add(received);
            }
        });
        greeterServer.publish(greeterAddress());

        JaxWsServerFactoryBean echo = new JaxWsServerFactoryBean();
        echo.setBus(BusFactory.getDefaultBus());
        echo.setAddress(echoAddress());
        echo.setServiceClass(EchoService.class);
        echo.setServiceBean(new EchoService() {
            @Override
            public String echo(String text) {
                return "echo " + text;
            }
        });
        echoServer = echo.create();

        captor = new AbstractAppender("CxfProducerOperationHeaderTest", null, null, false, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                producerLogEvents.add(event.toImmutable());
            }
        };
        captor.start();
        producerLogger = (Logger) LogManager.getLogger(CxfProducer.class);
        producerLogger.addAppender(captor);
    }

    @AfterEach
    public void stopServers() {
        if (producerLogger != null) {
            producerLogger.removeAppender(captor);
        }
        if (greeterServer != null) {
            greeterServer.stop();
        }
        if (echoServer != null) {
            echoServer.destroy();
        }
    }

    private static String greeterAddress() {
        return "http://localhost:" + greeterPort.getPort() + "/CxfProducerOperationHeaderTest/Greeter";
    }

    private static String echoAddress() {
        return "http://localhost:" + echoPort.getPort() + "/CxfProducerOperationHeaderTest/Echo";
    }

    private static String greeterUri(String options) {
        return "cxf://" + greeterAddress() + "?serviceClass=org.apache.hello_world_soap_http.Greeter&synchronous=true"
               + options;
    }

    private static Object paramsFor(String operation) {
        return switch (operation) {
            case "sayHi" -> null;
            case "greetMeLater" -> List.of(0L);
            default -> List.of("Willem");
        };
    }

    private Exchange send(String uri, Map<String, Object> headers, Object body) {
        return template.send(uri, e -> {
            e.setPattern(ExchangePattern.InOut);
            e.getIn().getHeaders().putAll(headers);
            e.getIn().setBody(body);
        });
    }

    private static String invokedOperation(Exchange reply) {
        BindingOperationInfo boi = reply.getProperty(BindingOperationInfo.class.getName(), BindingOperationInfo.class);
        assertNotNull(boi, "the producer sets the BindingOperationInfo property before it invokes the service");
        return boi.getName().getLocalPart();
    }

    private List<String> producerWarnings() {
        return producerLogEvents.stream()
                .filter(e -> e.getLevel().isMoreSpecificThan(Level.WARN))
                .map(e -> e.getMessage().getFormattedMessage())
                .toList();
    }

    @Test
    public void operationNameHeaderOfFormerNameIsReportedOnceAndNotSent() {
        // the operation the producer invokes when no header selects one
        String invoked = invokedOperation(send(greeterUri(""), Map.of(), paramsFor("sayHi")));
        producerLogEvents.clear();

        for (int i = 0; i < 3; i++) {
            Exchange reply = send(greeterUri(""), Map.of("operationName", "greetMe", "operationNamespace", NS),
                    paramsFor(invoked));
            // the header is not read: it does not select the operation
            assertEquals(invoked, invokedOperation(reply));
        }

        List<String> warnings = producerWarnings();
        assertEquals(1, warnings.size(), "warnings: " + warnings);
        String warning = warnings.get(0);
        assertTrue(warning.contains("operationName"), warning);
        assertTrue(warning.contains(CxfConstants.OPERATION_NAME), warning);
        assertTrue(warning.contains(CxfConstants.OPERATION_NAMESPACE), warning);

        // the headers are not sent to the service as HTTP headers
        assertFalse(serverHeaders.isEmpty());
        assertTrue(serverHeaders.stream().noneMatch(h -> h.containsKey("operationName")), "server headers: " + serverHeaders);
        assertTrue(serverHeaders.stream().noneMatch(h -> h.containsKey("operationNamespace")),
                "server headers: " + serverHeaders);
    }

    @Test
    public void operationNotSelectedOnServiceWithSeveralOperationsIsReportedOnce() {
        String invoked = invokedOperation(send(greeterUri(""), Map.of(), paramsFor("sayHi")));
        invokedOperation(send(greeterUri(""), Map.of(), paramsFor(invoked)));

        List<String> warnings = producerWarnings();
        assertEquals(1, warnings.size(), "warnings: " + warnings);
        String warning = warnings.get(0);
        assertTrue(warning.contains(CxfConstants.OPERATION_NAME), warning);
        assertTrue(warning.contains("defaultOperationName"), warning);
        assertTrue(warning.contains("{" + NS + "}" + invoked), warning);
    }

    @Test
    public void unknownOperationNamesTheHeadersAndTheOperationsOfTheService() {
        Exchange reply = send(greeterUri(""),
                Map.of(CxfConstants.OPERATION_NAME, "greetMe", CxfConstants.OPERATION_NAMESPACE, "http://wrong/ns"),
                List.of("Willem"));

        IllegalArgumentException e = assertInstanceOf(IllegalArgumentException.class, reply.getException());
        String message = e.getMessage();
        assertTrue(message.contains("{http://wrong/ns}greetMe"), message);
        assertTrue(message.contains(CxfConstants.OPERATION_NAME), message);
        assertTrue(message.contains(CxfConstants.OPERATION_NAMESPACE), message);
        assertTrue(message.contains("defaultOperationNamespace"), message);
        for (String operation : GREETER_OPERATIONS) {
            String listed = "{" + NS + "}" + operation;
            assertTrue(message.contains(listed + ",") || message.contains(listed + "]"), message);
        }
    }

    @Test
    public void operationNameHeaderSelectsEachOperationWithoutWarning() {
        for (String operation : GREETER_OPERATIONS) {
            Exchange reply = send(greeterUri(""), Map.of(CxfConstants.OPERATION_NAME, operation), paramsFor(operation));
            assertNull(reply.getException(), operation);
            assertEquals(operation, invokedOperation(reply));
        }
        Exchange reply = send(greeterUri(""),
                Map.of(CxfConstants.OPERATION_NAME, "greetMe", CxfConstants.OPERATION_NAMESPACE, NS), List.of("Willem"));
        assertEquals("Hello Willem", reply.getMessage().getBody(String.class));

        assertEquals(List.of(), producerWarnings());
        // the operation headers are not sent to the service as HTTP headers
        assertTrue(serverHeaders.stream().noneMatch(h -> h.containsKey(CxfConstants.OPERATION_NAME)));
        assertTrue(serverHeaders.stream().noneMatch(h -> h.containsKey(CxfConstants.OPERATION_NAMESPACE)));
    }

    @Test
    public void operationNameHeaderWinsOverHeaderOfFormerNameWithoutWarning() {
        // a route that maps an application header named operationName to CamelCxfOperationName
        Exchange reply = send(greeterUri(""),
                Map.of("operationName", "greetMe", CxfConstants.OPERATION_NAME, "greetMe"), List.of("Willem"));

        assertNull(reply.getException());
        assertEquals("Hello Willem", reply.getMessage().getBody(String.class));
        assertEquals(List.of(), producerWarnings());
    }

    @Test
    public void defaultOperationNameSelectsTheOperationWithoutWarning() {
        Exchange reply = send(greeterUri("&defaultOperationName=greetMe&defaultOperationNamespace=" + NS), Map.of(),
                List.of("Willem"));

        assertNull(reply.getException());
        assertEquals("greetMe", invokedOperation(reply));
        assertEquals("Hello Willem", reply.getMessage().getBody(String.class));
        assertEquals(List.of(), producerWarnings());
    }

    @Test
    public void serviceWithOneOperationIsInvokedWithoutWarning() {
        String uri = "cxf://" + echoAddress() + "?serviceClass=" + EchoService.class.getName() + "&synchronous=true";
        for (int i = 0; i < 2; i++) {
            Exchange reply = send(uri, Map.of(), List.of("Willem"));
            assertNull(reply.getException());
            assertEquals("echo", invokedOperation(reply));
            assertEquals("echo Willem", reply.getMessage().getBody(String.class));
        }
        assertEquals(List.of(), producerWarnings());
    }
}
