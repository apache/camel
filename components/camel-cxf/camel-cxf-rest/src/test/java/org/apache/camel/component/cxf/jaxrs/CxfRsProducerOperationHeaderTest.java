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
package org.apache.camel.component.cxf.jaxrs;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.apache.camel.component.cxf.jaxrs.testbean.Customer;
import org.apache.camel.component.cxf.jaxrs.testbean.CustomerService;
import org.apache.camel.component.cxf.jaxrs.testbean.CustomerServiceImpl;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.helpers.CastUtils;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How the producer finds the method to invoke with the proxy-based client API from the CamelCxfOperationName header,
 * what it reports when the header is not set, and which operation headers it sends to the service.
 */
public class CxfRsProducerOperationHeaderTest extends CamelTestSupport {
    private static AvailablePortFinder.Port port;

    private final List<Map<String, List<String>>> serverHeaders = new CopyOnWriteArrayList<>();
    private Server rsServer;

    @BeforeAll
    static void allocatePort() {
        port = AvailablePortFinder.find();
    }

    @AfterAll
    static void releasePort() {
        if (port != null) {
            port.release();
        }
    }

    @BeforeEach
    public void startServer() {
        JAXRSServerFactoryBean sf = new JAXRSServerFactoryBean();
        sf.setAddress(address());
        // CustomerServiceImpl repeats @PathParam on its methods, which hides the annotations of the interface from the
        // server: serve the annotated interface backed by the implementation
        sf.setResourceClasses(CustomerService.class);
        sf.setResourceProvider(CustomerService.class, new SingletonResourceProvider(new CustomerServiceImpl()));
        sf.getInInterceptors().add(new AbstractPhaseInterceptor<Message>(Phase.RECEIVE) {
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
        rsServer = sf.create();
    }

    @AfterEach
    public void stopServer() {
        if (rsServer != null) {
            rsServer.stop();
            rsServer.destroy();
        }
    }

    private static String address() {
        return "http://localhost:" + port.getPort() + "/CxfRsProducerOperationHeaderTest";
    }

    private static String proxyUri(String options) {
        return "cxfrs://" + address() + "?httpClientAPI=false&resourceClasses=" + CustomerService.class.getName() + options;
    }

    private static String httpUri() {
        return "cxfrs://" + address() + "?synchronous=true";
    }

    private Exchange send(String uri, Map<String, Object> headers, Object body) {
        return template.send(uri, e -> {
            e.setPattern(ExchangePattern.InOut);
            e.getIn().getHeaders().putAll(headers);
            e.getIn().setBody(body);
        });
    }

    private static void assertCustomer123(Exchange reply) {
        assertNull(reply.getException());
        Customer customer = assertInstanceOf(Customer.class, reply.getMessage().getBody());
        assertEquals(123, customer.getId());
    }

    @ParameterizedTest
    @ValueSource(strings = { "&synchronous=true", "" })
    public void missingOperationNameHeaderIsNamedInTheError(String options) {
        Exchange reply = send(proxyUri(options), Map.of(), "123");

        NoSuchMethodException e = assertInstanceOf(NoSuchMethodException.class, reply.getException());
        String message = e.getMessage();
        assertTrue(message.contains("The " + CxfConstants.OPERATION_NAME + " header is not set"), message);
        assertTrue(message.contains("httpClientAPI=false"), message);
        assertFalse(message.contains(" operationName "), message);
        assertTrue(serverHeaders.isEmpty(), "server headers: " + serverHeaders);
    }

    @ParameterizedTest
    @ValueSource(strings = { "&synchronous=true", "" })
    public void operationNameHeaderOfFormerNameIsNamedInTheError(String options) {
        Exchange reply = send(proxyUri(options), Map.of("operationName", "getCustomer"), "123");

        // the header is not read: it does not select the method
        NoSuchMethodException e = assertInstanceOf(NoSuchMethodException.class, reply.getException());
        String message = e.getMessage();
        assertTrue(message.contains("The " + CxfConstants.OPERATION_NAME + " header is not set"), message);
        assertTrue(message.contains("does not read the operationName header"), message);
        assertTrue(serverHeaders.isEmpty(), "server headers: " + serverHeaders);
    }

    @Test
    public void operationNameHeaderOfFormerNameIsNotSentAsHttpHeader() {
        // proxy-based client API: a route that maps an application header named operationName to CamelCxfOperationName
        Exchange proxyReply = send(proxyUri("&synchronous=true"),
                Map.of("operationName", "getCustomer", CxfConstants.OPERATION_NAME, "getCustomer", "key", "value"), "123");
        assertCustomer123(proxyReply);

        // HTTP centric client API, which does not read the operation headers
        Exchange httpReply = send(httpUri(), Map.of("operationName", "getCustomer", Exchange.HTTP_METHOD, "GET",
                Exchange.HTTP_PATH, "/customerservice/customers/123", "key", "value"), null);
        assertNull(httpReply.getException());
        assertEquals(200, httpReply.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));

        assertEquals(2, serverHeaders.size(), "server headers: " + serverHeaders);
        assertTrue(serverHeaders.stream().noneMatch(h -> h.containsKey("operationName")), "server headers: " + serverHeaders);
        // the other headers are still sent
        assertTrue(serverHeaders.stream().allMatch(h -> List.of("value").equals(h.get("key"))),
                "server headers: " + serverHeaders);
    }

    @ParameterizedTest
    @ValueSource(strings = { "&synchronous=true", "" })
    public void operationNameHeaderSelectsTheMethod(String options) {
        Exchange reply = send(proxyUri(options), Map.of(CxfConstants.OPERATION_NAME, "getCustomer"), "123");

        assertCustomer123(reply);
        assertEquals(1, serverHeaders.size(), "server headers: " + serverHeaders);
        assertFalse(serverHeaders.get(0).containsKey(CxfConstants.OPERATION_NAME), "server headers: " + serverHeaders);
    }

    @Test
    public void operationNameHeaderWinsOverHeaderOfFormerName() {
        Exchange reply = send(proxyUri("&synchronous=true"),
                Map.of("operationName", "getCustomers", CxfConstants.OPERATION_NAME, "getCustomer"), "123");

        assertCustomer123(reply);
    }

    @Test
    public void unknownMethodNameIsNamedInTheError() {
        Exchange reply = send(proxyUri("&synchronous=true"), Map.of(CxfConstants.OPERATION_NAME, "noSuchMethod"), "123");

        NoSuchMethodException e = assertInstanceOf(NoSuchMethodException.class, reply.getException());
        assertEquals("Cannot find method with name: noSuchMethod having parameters assignable from: [class java.lang.String]",
                e.getMessage());
        assertTrue(serverHeaders.isEmpty(), "server headers: " + serverHeaders);
    }
}
