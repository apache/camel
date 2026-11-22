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

import java.security.Principal;
import java.util.List;
import java.util.Map;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.FailedToStartRouteException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.cxf.binding.soap.SoapFault;
import org.apache.cxf.ws.security.wss4j.WSS4JInInterceptor;
import org.apache.cxf.ws.security.wss4j.WSS4JOutInterceptor;
import org.apache.wss4j.common.ext.WSPasswordCallback;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WS-Security UsernameToken with the wsSecurity options of the endpoint, for a WSDL without and with a
 * WS-SecurityPolicy, and #bean: references in properties.*.
 */
class CxfWsSecurityUsernameTokenTest extends CamelTestSupport {

    private static final String REQUEST
            = "<getOrder xmlns=\"http://camel.apache.org/wssec/orders\"><orderId>1</orderId></getOrder>";

    private static AvailablePortFinder.Port port;

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

    private static String address(String path) {
        return "http://localhost:" + port.getPort() + "/CxfWsSecurityUsernameTokenTest/" + path;
    }

    private static String server(String path, String wsdl, String options) {
        return "cxf://" + address(path) + "?wsdlURL=classpath:wssec/" + wsdl + "&dataFormat=PAYLOAD" + options;
    }

    private static String client(String path, String wsdl, String options) {
        return "cxf://" + address(path) + "?wsdlURL=classpath:wssec/" + wsdl
               + "&dataFormat=PAYLOAD&defaultOperationName=getOrder" + options;
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getRegistry().bind("users", Map.of("bob", "builder", "carol", "secret"));
        context.getRegistry().bind("serverCallback", new FixedPasswordCallbackHandler("alice", "wonderland"));
        context.getRegistry().bind("clientCallback", new FixedPasswordCallbackHandler("alice", "wonderland"));
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // no WS-Policy in the WSDL: one user, PasswordText
                from(server("text", "orders.wsdl", "&wsSecurityUsername=alice&wsSecurityPassword=wonderland"))
                        .routeId("text").process(CxfWsSecurityUsernameTokenTest::reply);
                // no WS-Policy in the WSDL: several users from a map bean, PasswordDigest
                from(server("digest", "orders.wsdl", "&wsSecurityCredentials=#bean:users&wsSecurityPasswordType=Digest"))
                        .routeId("digest").process(CxfWsSecurityUsernameTokenTest::reply);
                // a WS-SecurityPolicy in the WSDL: the policy asks for the UsernameToken, the handler checks it
                from(server("policy", "orders-ut.wsdl", "&wsSecurityCallbackHandler=#bean:serverCallback"))
                        .routeId("policy").process(CxfWsSecurityUsernameTokenTest::reply);
                // the CXF property set in properties.* with a #bean: reference
                from(server("properties", "orders-ut.wsdl", "&properties.security.callback-handler=#bean:serverCallback"))
                        .routeId("properties").process(CxfWsSecurityUsernameTokenTest::reply);
            }
        };
    }

    private static void reply(Exchange exchange) {
        Subject subject = exchange.getIn().getHeader(CxfConstants.AUTHENTICATION, Subject.class);
        String user = subject == null
                ? "none"
                : subject.getPrincipals().stream().map(Principal::getName).findFirst().orElse("none");
        exchange.getMessage().setBody("<getOrderResponse xmlns=\"http://camel.apache.org/wssec/orders\">"
                                      + "<orderId>1</orderId><user>" + user + "</user></getOrderResponse>");
    }

    private String call(String uri) {
        Exchange exchange = template.request(uri, e -> e.getIn().setBody(REQUEST));
        if (exchange.getException() != null) {
            throw new AssertionError("Unexpected exception", exchange.getException());
        }
        return exchange.getMessage().getBody(String.class);
    }

    private void callRejected(String uri) {
        Exchange exchange = template.request(uri, e -> e.getIn().setBody(REQUEST));
        SoapFault fault = assertInstanceOf(SoapFault.class, exchange.getException());
        assertTrue(fault.getMessage().contains("security"), fault.getMessage());
    }

    @Test
    void textWithoutPolicy() {
        String reply = call(client("text", "orders.wsdl", "&wsSecurityUsername=alice&wsSecurityPassword=wonderland"));
        assertTrue(reply.contains("<user>alice</user>"), reply);
    }

    @Test
    void textWithoutPolicyWrongPassword() {
        callRejected(client("text", "orders.wsdl", "&wsSecurityUsername=alice&wsSecurityPassword=wrong"));
    }

    @Test
    void textWithoutPolicyNoToken() {
        callRejected(client("text", "orders.wsdl", ""));
    }

    @Test
    void textWithoutPolicyClientCallbackHandler() {
        String reply = call(
                client("text", "orders.wsdl", "&wsSecurityUsername=alice&wsSecurityCallbackHandler=#bean:clientCallback"));
        assertTrue(reply.contains("<user>alice</user>"), reply);
    }

    @Test
    void digestWithoutPolicy() {
        String uri = client("digest", "orders.wsdl",
                "&wsSecurityUsername=bob&wsSecurityPassword=builder&wsSecurityPasswordType=Digest");
        String reply = call(uri);
        assertTrue(reply.contains("<user>bob</user>"), reply);

        String uri2 = client("digest", "orders.wsdl",
                "&wsSecurityUsername=carol&wsSecurityPassword=secret&wsSecurityPasswordType=Digest");
        reply = call(uri2);
        assertTrue(reply.contains("<user>carol</user>"), reply);
    }

    @Test
    void digestWithoutPolicyWrongPassword() {
        callRejected(client("digest", "orders.wsdl",
                "&wsSecurityUsername=bob&wsSecurityPassword=wrong&wsSecurityPasswordType=Digest"));
    }

    @Test
    void digestWithoutPolicyUnknownUser() {
        callRejected(client("digest", "orders.wsdl",
                "&wsSecurityUsername=mallory&wsSecurityPassword=builder&wsSecurityPasswordType=Digest"));
    }

    @Test
    void digestRequiredTextRejected() {
        // the consumer requires PasswordDigest
        callRejected(client("digest", "orders.wsdl", "&wsSecurityUsername=bob&wsSecurityPassword=builder"));
    }

    @Test
    void withPolicy() throws Exception {
        String uri = client("policy", "orders-ut.wsdl", "&wsSecurityUsername=alice&wsSecurityPassword=wonderland");
        String reply = call(uri);
        assertTrue(reply.contains("<user>alice</user>"), reply);

        // the policy sends and checks the token: no plain WSS4J interceptors as well
        CxfProducer producer = (CxfProducer) context.getEndpoint(uri).createProducer();
        producer.start();
        try {
            assertFalse(hasInterceptor(producer.getClient().getEndpoint().getOutInterceptors(), WSS4JOutInterceptor.class));
        } finally {
            producer.stop();
        }
        CxfConsumer consumer = (CxfConsumer) context.getRoute("policy").getConsumer();
        assertFalse(hasInterceptor(consumer.getServer().getEndpoint().getInInterceptors(), WSS4JInInterceptor.class));
    }

    @Test
    void withoutPolicyAddsInterceptors() throws Exception {
        String uri = client("text", "orders.wsdl", "&wsSecurityUsername=alice&wsSecurityPassword=wonderland");
        CxfProducer producer = (CxfProducer) context.getEndpoint(uri).createProducer();
        producer.start();
        try {
            assertTrue(hasInterceptor(producer.getClient().getEndpoint().getOutInterceptors(), WSS4JOutInterceptor.class));
        } finally {
            producer.stop();
        }
        CxfConsumer consumer = (CxfConsumer) context.getRoute("text").getConsumer();
        assertTrue(hasInterceptor(consumer.getServer().getEndpoint().getInInterceptors(), WSS4JInInterceptor.class));
    }

    @Test
    void withPolicyWrongPassword() {
        callRejected(client("policy", "orders-ut.wsdl", "&wsSecurityUsername=alice&wsSecurityPassword=wrong"));
    }

    @Test
    void withPolicyPropertiesBeanReference() {
        CxfEndpoint endpoint = (CxfEndpoint) context.getRoute("properties").getEndpoint();
        assertSame(context.getRegistry().lookupByName("serverCallback"),
                endpoint.getProperties().get("security.callback-handler"));

        // the client side with the CXF properties of before, which work as the WSDL has a policy
        String reply = call(client("properties", "orders-ut.wsdl",
                "&properties.security.username=alice&properties.security.password=wonderland"));
        assertTrue(reply.contains("<user>alice</user>"), reply);

        callRejected(client("properties", "orders-ut.wsdl",
                "&properties.security.username=alice&properties.security.password=wrong"));
    }

    @Test
    void propertiesOnlyBeanPrefixResolved() {
        // a value that only starts with # is free-form CXF configuration, and not a bean lookup
        CxfEndpoint endpoint = context.getEndpoint(
                client("text", "orders.wsdl", "&properties.security.password=#secret"), CxfEndpoint.class);
        assertEquals("#secret", endpoint.getProperties().get("security.password"));
    }

    @Test
    void propertiesUnknownBean() {
        Exception e = assertThrows(Exception.class, () -> context.getEndpoint(
                client("text", "orders.wsdl", "&properties.security.callback-handler=#bean:doesNotExist")));
        assertTrue(e.getMessage().contains("doesNotExist"), e.getMessage());
    }

    @Test
    void clientWithoutUsername() {
        Exception e = assertThrows(Exception.class, () -> {
            CxfProducer producer = (CxfProducer) context.getEndpoint(
                    client("text", "orders.wsdl", "&wsSecurityPassword=wonderland")).createProducer();
            producer.start();
        });
        assertTrue(e.getMessage().contains("wsSecurityUsername"), e.getMessage());
    }

    @Test
    void rawNotSupported() {
        Exception e = assertThrows(Exception.class, () -> context.getEndpoint(
                "cxf://" + address("raw") + "?wsdlURL=classpath:wssec/orders.wsdl&dataFormat=RAW"
                                                                              + "&wsSecurityUsername=alice&wsSecurityPassword=wonderland")
                .createProducer().start());
        assertTrue(e.getMessage().contains("RAW"), e.getMessage());
    }

    @Test
    void serverWithoutPassword() throws Exception {
        FailedToStartRouteException e = assertThrows(FailedToStartRouteException.class, () -> {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from(server("nopassword", "orders.wsdl", "&wsSecurityUsername=alice")).routeId("nopassword")
                            .process(CxfWsSecurityUsernameTokenTest::reply);
                }
            });
        });
        Throwable cause = e;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        assertTrue(cause.getMessage().contains("wsSecurityCredentials"), cause.getMessage());
    }

    private static boolean hasInterceptor(List<?> interceptors, Class<?> type) {
        return interceptors.stream().anyMatch(type::isInstance);
    }

    /**
     * Sets the password of one user, as a server or a client password callback handler.
     */
    static final class FixedPasswordCallbackHandler implements CallbackHandler {
        private final String user;
        private final String password;

        FixedPasswordCallbackHandler(String user, String password) {
            this.user = user;
            this.password = password;
        }

        @Override
        public void handle(Callback[] callbacks) {
            for (Callback cb : callbacks) {
                if (cb instanceof WSPasswordCallback pc && user.equals(pc.getIdentifier())) {
                    pc.setPassword(password);
                }
            }
        }
    }
}
