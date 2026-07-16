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
package org.apache.camel.component.jms;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.concurrent.TimeUnit;

import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSException;
import jakarta.jms.MessageProducer;
import jakarta.jms.Session;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ExchangeTimedOutException;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.TransientCamelContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Tests that when a JMS send fails after the reply correlation has been registered, the AsyncCallback is invoked
 * exactly once (not a second time by the timeout handler).
 *
 * @see <a href="https://issues.apache.org/jira/browse/CAMEL-24073">CAMEL-24073</a>
 */
public class JmsInOutSendFailureCallbackTest extends AbstractJMSTest {

    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new TransientCamelContextExtension();

    protected CamelContext context;
    protected ProducerTemplate template;

    @Test
    public void testCallbackInvokedOnceOnSendFailure() throws Exception {
        Exchange result = template.send("direct:JmsInOutSendFailureCallbackTest", ExchangePattern.InOut,
                p -> p.getIn().setBody("Hello"));

        assertNotNull(result.getException());
        assertFalse(result.getException() instanceof ExchangeTimedOutException,
                "Should fail with JMS send exception, not ExchangeTimedOutException");

        // wait past the requestTimeout (2s) and verify the timeout handler does not
        // overwrite the exception with ExchangeTimedOutException via a second callback
        Exception originalException = result.getException();
        await().during(3, TimeUnit.SECONDS)
                .atMost(4, TimeUnit.SECONDS)
                .untilAsserted(() -> assertSame(originalException, result.getException(),
                        "Exception changed after send failure - timeout handler fired a second callback"));
    }

    @Override
    public String getComponentName() {
        return "activemq";
    }

    @Override
    protected JmsComponent setupComponent(
            CamelContext camelContext, ConnectionFactory connectionFactory, String componentName) {
        ConnectionFactory failingCf = createFailingSendConnectionFactory(connectionFactory);
        return jmsComponentAutoAcknowledge(failingCf);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:JmsInOutSendFailureCallbackTest")
                        .to(ExchangePattern.InOut,
                                "activemq:queue:JmsInOutSendFailureCallbackTest?requestTimeout=2000");
            }
        };
    }

    @Override
    public CamelContextExtension getCamelContextExtension() {
        return camelContextExtension;
    }

    @BeforeEach
    void setUpRequirements() {
        context = camelContextExtension.getContext();
        template = camelContextExtension.getProducerTemplate();
    }

    private static ConnectionFactory createFailingSendConnectionFactory(ConnectionFactory delegate) {
        return proxyOf(ConnectionFactory.class, delegate, (proxy, method, args) -> {
            Object result = method.invoke(delegate, args);
            return result instanceof Connection conn ? wrapConnection(conn) : result;
        });
    }

    private static Connection wrapConnection(Connection delegate) {
        return proxyOf(Connection.class, delegate, (proxy, method, args) -> {
            Object result = method.invoke(delegate, args);
            return result instanceof Session session ? wrapSession(session) : result;
        });
    }

    private static Session wrapSession(Session delegate) {
        return proxyOf(Session.class, delegate, (proxy, method, args) -> {
            Object result = method.invoke(delegate, args);
            return result instanceof MessageProducer producer ? wrapProducer(producer) : result;
        });
    }

    private static MessageProducer wrapProducer(MessageProducer delegate) {
        return proxyOf(MessageProducer.class, delegate, (proxy, method, args) -> {
            if ("send".equals(method.getName())) {
                throw new JMSException("Simulated send failure: broker rejected message");
            }
            return method.invoke(delegate, args);
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxyOf(Class<T> iface, T delegate, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[] { iface }, handler);
    }
}
