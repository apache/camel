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

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ExchangeTimedOutException;
import org.apache.camel.Message;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.activemq.common.ConnectionFactoryHelper;
import org.apache.camel.test.infra.activemq.services.LegacyEmbeddedBroker;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;
import static org.apache.camel.test.infra.activemq.common.ConnectionFactoryHelper.createConnectionFactory;
import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class JmsRouteRequestReplyTest extends CamelTestSupport {

    protected static final String REPLY_TO_DESTINATION_SELECTOR_NAME = "camelProducer";
    protected static final String COMPONENT_NAME = "amq";
    protected static final String COMPONENT_NAME_1 = "amq1";
    protected static String endpointUriA = COMPONENT_NAME + ":queue:JmsRouteRequestReplyTest.a";
    protected static String endpointUriB = COMPONENT_NAME + ":queue:JmsRouteRequestReplyTest.b";
    protected static String endpointUriB1 = COMPONENT_NAME_1 + ":queue:JmsRouteRequestReplyTest.b";
    // note that the replyTo both A and B endpoints share the persistent replyTo queue,
    // which is one more way to verify that reply listeners of A and B endpoints don't steal each other messages
    protected static String endpointReplyToUriA
            = COMPONENT_NAME + ":queue:JmsRouteRequestReplyTest.a?replyTo=queue:JmsRouteRequestReplyTest.a.reply";
    protected static String endpointReplyToUriB
            = COMPONENT_NAME + ":queue:JmsRouteRequestReplyTest.b?replyTo=queue:JmsRouteRequestReplyTest.a.reply";

    protected static String request = "Hello World";
    protected static String expectedReply = "Re: " + request;
    protected static int maxTasks = 20;
    protected static int maxServerTasks = 1;
    protected static int maxCalls = 5;
    protected static AtomicBoolean inited = new AtomicBoolean();
    protected static Map<String, ContextBuilder> contextBuilders = new HashMap<>();
    protected static Map<String, RouteBuilder> routeBuilders = new HashMap<>();

    protected TestInfo testInfo;

    private interface ContextBuilder {
        CamelContext buildContext(CamelContext context);
    }

    public static class SingleNodeDeadEndRouteBuilder extends RouteBuilder {
        @Override
        public void configure() {
            from(endpointUriA)
                    // We are not expect the response here
                    .setExchangePattern(ExchangePattern.InOnly).process(e -> {
                        // do nothing
                    });
        }
    }

    public static class SingleNodeRouteBuilder extends RouteBuilder {
        @Override
        public void configure() {
            from(endpointUriA).process(e -> {
                String request = e.getIn().getBody(String.class);
                e.getMessage().setBody(expectedReply + request.substring(request.indexOf('-')));
            });
        }
    }

    public static class MultiNodeRouteBuilder extends RouteBuilder {
        @Override
        public void configure() {
            from(endpointUriA).to(endpointUriB);
            from(endpointUriB).process(e -> {
                String request = e.getIn().getBody(String.class);
                e.getMessage().setBody(expectedReply + request.substring(request.indexOf('-')));
            });
        }
    }

    public static class MultiNodeReplyToRouteBuilder extends RouteBuilder {
        @Override
        public void configure() {
            from(endpointUriA).to(endpointReplyToUriB);
            from(endpointUriB).process(e -> {
                Message in = e.getIn();
                Message out = e.getMessage();
                String selectorValue = in.getHeader(REPLY_TO_DESTINATION_SELECTOR_NAME, String.class);
                String request = in.getBody(String.class);
                out.setHeader(REPLY_TO_DESTINATION_SELECTOR_NAME, selectorValue);
                out.setBody(expectedReply + request.substring(request.indexOf('-')));
            });
        }
    }

    public static class MultiNodeDiffCompRouteBuilder extends RouteBuilder {
        @Override
        public void configure() {
            from(endpointUriA).to(endpointUriB1);
            from(endpointUriB1).process(e -> {
                String request = e.getIn().getBody(String.class);
                e.getMessage().setBody(expectedReply + request.substring(request.indexOf('-')));
            });
        }
    }

    public static class ContextBuilderMessageID implements ContextBuilder {
        @Override
        public CamelContext buildContext(CamelContext context) {
            final String brokerUrl = LegacyEmbeddedBroker.createBrokerUrl();
            ConnectionFactory connectionFactory = ConnectionFactoryHelper.createConnectionFactory(brokerUrl, null);

            JmsComponent jmsComponent = jmsComponentAutoAcknowledge(connectionFactory);
            jmsComponent.getConfiguration().setUseMessageIDAsCorrelationID(true);
            jmsComponent.getConfiguration().setConcurrentConsumers(maxServerTasks);
            context.addComponent(COMPONENT_NAME, jmsComponent);
            return context;
        }
    }

    protected static void init() {
        if (inited.compareAndSet(false, true)) {

            ContextBuilder contextBuilderMessageID = new ContextBuilderMessageID();

            ContextBuilder contextBuilderCorrelationID = context -> {
                final String brokerUrl = LegacyEmbeddedBroker.createBrokerUrl();
                ConnectionFactory connectionFactory = createConnectionFactory(brokerUrl, null);

                JmsComponent jms = jmsComponentAutoAcknowledge(connectionFactory);
                jms.getConfiguration().setUseMessageIDAsCorrelationID(false);
                jms.getConfiguration().setConcurrentConsumers(maxServerTasks);
                context.addComponent(COMPONENT_NAME, jms);
                return context;
            };

            ContextBuilder contextBuilderMessageIDNamedReplyToSelector = context -> {
                final String brokerUrl = LegacyEmbeddedBroker.createBrokerUrl();
                ConnectionFactory connectionFactory = createConnectionFactory(brokerUrl, null);

                JmsComponent jms = jmsComponentAutoAcknowledge(connectionFactory);
                jms.getConfiguration().setReplyToDestinationSelectorName(REPLY_TO_DESTINATION_SELECTOR_NAME);
                jms.getConfiguration().setUseMessageIDAsCorrelationID(true);
                jms.getConfiguration().setConcurrentConsumers(maxServerTasks);
                context.addComponent(COMPONENT_NAME, jms);
                return context;
            };

            ContextBuilder contextBuilderCorrelationIDNamedReplyToSelector = context -> {
                final String brokerUrl = LegacyEmbeddedBroker.createBrokerUrl();
                ConnectionFactory connectionFactory = createConnectionFactory(brokerUrl, null);

                JmsComponent jms = jmsComponentAutoAcknowledge(connectionFactory);
                jms.getConfiguration().setReplyToDestinationSelectorName(REPLY_TO_DESTINATION_SELECTOR_NAME);
                jms.getConfiguration().setUseMessageIDAsCorrelationID(false);
                jms.getConfiguration().setConcurrentConsumers(maxServerTasks);
                context.addComponent(COMPONENT_NAME, jms);
                return context;
            };

            ContextBuilder contextBuilderCorrelationIDDiffComp = context -> {
                final String brokerUrl = LegacyEmbeddedBroker.createBrokerUrl();
                ConnectionFactory connectionFactory = createConnectionFactory(brokerUrl, null);
                JmsComponent jms = jmsComponentAutoAcknowledge(connectionFactory);
                jms.getConfiguration().setConcurrentConsumers(maxServerTasks);
                context.addComponent(COMPONENT_NAME, jms);

                JmsComponent jms1 = jmsComponentAutoAcknowledge(connectionFactory);
                jms1.getConfiguration().setUseMessageIDAsCorrelationID(false);
                jms1.getConfiguration().setConcurrentConsumers(maxServerTasks);
                context.addComponent(COMPONENT_NAME_1, jms1);
                return context;
            };

            ContextBuilder contextBuilderMessageIDDiffComp = context -> {
                final String brokerUrl = LegacyEmbeddedBroker.createBrokerUrl();
                ConnectionFactory connectionFactory = createConnectionFactory(brokerUrl, null);
                JmsComponent jms = jmsComponentAutoAcknowledge(connectionFactory);
                jms.getConfiguration().setUseMessageIDAsCorrelationID(true);
                jms.getConfiguration().setConcurrentConsumers(maxServerTasks);
                context.addComponent(COMPONENT_NAME, jms);

                JmsComponent jms1 = jmsComponentAutoAcknowledge(connectionFactory);
                jms1.getConfiguration().setUseMessageIDAsCorrelationID(true);
                jms1.getConfiguration().setConcurrentConsumers(maxServerTasks);
                context.addComponent(COMPONENT_NAME_1, jms1);
                return context;
            };

            contextBuilders.put("testUseMessageIDAsCorrelationID", contextBuilderMessageID);

            contextBuilders.put("testUseCorrelationID", contextBuilderCorrelationID);
            contextBuilders.put("testUseMessageIDAsCorrelationIDMultiNode", contextBuilderMessageID);
            contextBuilders.put("testUseCorrelationIDMultiNode", contextBuilderCorrelationID);

            contextBuilders.put("testUseMessageIDAsCorrelationIDPersistReplyToMultiNode", contextBuilderMessageID);
            contextBuilders.put("testUseCorrelationIDPersistReplyToMultiNode", contextBuilderCorrelationID);

            contextBuilders.put("testUseMessageIDAsCorrelationIDPersistMultiReplyToMultiNode", contextBuilderMessageID);
            // contextBuilders.put("testUseCorrelationIDPersistMultiReplyToMultiNode", contextBuilderCorrelationID);

            contextBuilders.put("testUseMessageIDAsCorrelationIDPersistMultiReplyToWithNamedSelectorMultiNode",
                    contextBuilderMessageIDNamedReplyToSelector);

            contextBuilders.put("testUseCorrelationIDPersistMultiReplyToWithNamedSelectorMultiNode",
                    contextBuilderCorrelationIDNamedReplyToSelector);

            contextBuilders.put("testUseCorrelationIDMultiNodeDiffComponents", contextBuilderCorrelationIDDiffComp);
            contextBuilders.put("testUseMessageIDAsCorrelationIDMultiNodeDiffComponents", contextBuilderMessageIDDiffComp);
            contextBuilders.put("testUseMessageIDAsCorrelationIDTimeout", contextBuilderMessageID);
            contextBuilders.put("testUseCorrelationIDTimeout", contextBuilderMessageID);

            routeBuilders.put("testUseMessageIDAsCorrelationID", new SingleNodeRouteBuilder());
            routeBuilders.put("testUseMessageIDAsCorrelationIDReplyToTempDestinationPerComponent",
                    new SingleNodeRouteBuilder());
            routeBuilders.put("testUseMessageIDAsCorrelationIDReplyToTempDestinationPerProducer", new SingleNodeRouteBuilder());
            routeBuilders.put("testUseCorrelationID", new SingleNodeRouteBuilder());
            routeBuilders.put("testUseMessageIDAsCorrelationIDMultiNode", new MultiNodeRouteBuilder());
            routeBuilders.put("testUseCorrelationIDMultiNode", new MultiNodeRouteBuilder());

            routeBuilders.put("testUseMessageIDAsCorrelationIDPersistReplyToMultiNode", new MultiNodeRouteBuilder());
            routeBuilders.put("testUseCorrelationIDPersistReplyToMultiNode", new MultiNodeRouteBuilder());

            routeBuilders.put("testUseMessageIDAsCorrelationIDPersistMultiReplyToMultiNode",
                    new MultiNodeReplyToRouteBuilder());
            // routeBuilders.put("testUseCorrelationIDPersistMultiReplyToMultiNode", new MultiNodeReplyToRouteBuilder());

            routeBuilders.put("testUseMessageIDAsCorrelationIDPersistMultiReplyToWithNamedSelectorMultiNode",
                    new MultiNodeReplyToRouteBuilder());

            routeBuilders.put("testUseCorrelationIDPersistMultiReplyToWithNamedSelectorMultiNode",
                    new MultiNodeReplyToRouteBuilder());

            routeBuilders.put("testUseCorrelationIDMultiNodeDiffComponents", new MultiNodeDiffCompRouteBuilder());
            routeBuilders.put("testUseMessageIDAsCorrelationIDMultiNodeDiffComponents", new MultiNodeDiffCompRouteBuilder());
            routeBuilders.put("testUseMessageIDAsCorrelationIDTimeout", new SingleNodeDeadEndRouteBuilder());
            routeBuilders.put("testUseCorrelationIDTimeout", new SingleNodeDeadEndRouteBuilder());
        }
    }

    public class Task implements Callable<Task> {
        private final AtomicInteger counter;
        private final String fromUri;
        private volatile boolean ok = true;
        private volatile String message = "";

        public Task(AtomicInteger counter, String fromUri) {
            this.counter = counter;
            this.fromUri = fromUri;
        }

        @Override
        public Task call() {
            for (int i = 0; i < maxCalls; i++) {
                int callId = counter.incrementAndGet();
                Object reply = "";
                try {
                    reply = template.requestBody(fromUri, request + "-" + callId);
                } catch (RuntimeCamelException e) {
                    // expected in some cases
                }
                if (!reply.equals(expectedReply + "-" + callId)) {
                    ok = false;
                    message = "Unexpected reply. Expected: '" + expectedReply + "-" + callId
                              + "'; Received: '" + reply + "'";
                }
            }
            return this;
        }

        public void assertSuccess() {
            assertTrue(ok, message);
        }
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        init();
        super.setUp();
    }

    @Test
    public void testUseMessageIDAsCorrelationID() throws Exception {
        runRequestReplyThreaded(endpointUriA);
    }

    @Test
    public void testUseCorrelationID() throws Exception {
        runRequestReplyThreaded(endpointUriA);
    }

    @Test
    public void testUseMessageIDAsCorrelationIDMultiNode() throws Exception {
        runRequestReplyThreaded(endpointUriA);
    }

    @Test
    public void testUseCorrelationIDMultiNode() throws Exception {
        runRequestReplyThreaded(endpointUriA);
    }

    @Test
    public void testUseMessageIDAsCorrelationIDPersistReplyToMultiNode() throws Exception {
        runRequestReplyThreaded(endpointReplyToUriA);
    }

    @Test
    public void testUseCorrelationIDPersistReplyToMultiNode() throws Exception {
        runRequestReplyThreaded(endpointUriA);
    }

    // (1)
    // note this is an inefficient way of correlating replies to a persistent queue
    // a consumer will have to be created for each reply message
    // see testUseMessageIDAsCorrelationIDPersistMultiReplyToWithNamedSelectorMultiNode
    // or testCorrelationIDPersistMultiReplyToWithNamedSelectorMultiNode
    // for a faster way to do this. Note however that in this case the message copy has to occur
    // between consumer -> producer as the selector value needs to be propagated to the ultimate
    // destination, which in turn will copy this value back into the reply message
    @Test
    public void testUseMessageIDAsCorrelationIDPersistMultiReplyToMultiNode() throws Exception {
        int oldMaxTasks = maxTasks;
        int oldMaxServerTasks = maxServerTasks;
        int oldMaxCalls = maxCalls;

        maxTasks = 10;
        maxServerTasks = 1;
        maxCalls = 2;

        try {
            runRequestReplyThreaded(endpointUriA);
        } finally {
            maxTasks = oldMaxTasks;
            maxServerTasks = oldMaxServerTasks;
            maxCalls = oldMaxCalls;
        }
    }

    // see (1)
    @Test
    @Disabled
    public void testUseCorrelationIDPersistMultiReplyToMultiNode() throws Exception {
        int oldMaxTasks = maxTasks;
        int oldMaxServerTasks = maxServerTasks;
        int oldMaxCalls = maxCalls;

        maxTasks = 10;
        maxServerTasks = 1;
        maxCalls = 2;

        try {
            runRequestReplyThreaded(endpointUriA);
        } finally {
            maxTasks = oldMaxTasks;
            maxServerTasks = oldMaxServerTasks;
            maxCalls = oldMaxCalls;
        }
    }

    @Test
    public void testUseMessageIDAsCorrelationIDPersistMultiReplyToWithNamedSelectorMultiNode() throws Exception {
        runRequestReplyThreaded(endpointUriA);
    }

    @Test
    public void testUseCorrelationIDPersistMultiReplyToWithNamedSelectorMultiNode() throws Exception {
        runRequestReplyThreaded(endpointUriA);
    }

    @Test
    public void testUseCorrelationIDTimeout() {
        Object reply = "";
        try {
            // set the timeout for the request, specifically. At this point it is too late to re-configure
            template.requestBodyAndHeader(endpointUriA, request, JmsConstants.JMS_REQUEST_TIMEOUT, "1000");
            fail("Should have thrown exception");
        } catch (RuntimeCamelException e) {
            assertIsInstanceOf(ExchangeTimedOutException.class, e.getCause());
        }
        assertEquals("", reply);
    }

    @Test
    public void testUseMessageIDAsCorrelationIDTimeout() {
        Object reply = "";
        try {
            // set the timeout for the request, specifically. At this point it is too late to re-configure
            reply = template.requestBodyAndHeader(endpointUriA, request, JmsConstants.JMS_REQUEST_TIMEOUT, "1000");
            fail("Should have thrown exception");
        } catch (RuntimeCamelException e) {
            assertIsInstanceOf(ExchangeTimedOutException.class, e.getCause());
        }
        assertEquals("", reply);
    }

    @Test
    public void testUseCorrelationIDMultiNodeDiffComponents() throws Exception {
        runRequestReplyThreaded(endpointUriA);
    }

    @Test
    public void testUseMessageIDAsCorrelationIDMultiNodeDiffComponents() throws Exception {
        runRequestReplyThreaded(endpointUriA);
    }

    protected void runRequestReplyThreaded(String fromUri) throws Exception {
        // start template
        template.start();

        ExecutorService executor = context.getExecutorServiceManager().newFixedThreadPool(this, "Task", maxTasks);
        CompletionService<Task> completionService = new ExecutorCompletionService<>(executor);

        final AtomicInteger counter = new AtomicInteger(-1);
        for (int i = 0; i < maxTasks; i++) {
            Task task = new Task(counter, fromUri);
            completionService.submit(task);
        }

        for (int i = 0; i < maxTasks; i++) {
            Future<Task> future = completionService.take();
            Task task = future.get(60, TimeUnit.SECONDS);
            assertNotNull(task, "Should complete the task");
            task.assertSuccess();
        }

        context.getExecutorServiceManager().shutdownNow(executor);
    }

    @BeforeEach
    protected void setUp(TestInfo testInfo) {
        this.testInfo = testInfo;
    }

    protected String getTestMethodName() {
        return testInfo.getTestMethod().map(Method::getName).orElse("<unknown>");
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        return contextBuilders.get(getTestMethodName()).buildContext(camelContext);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return routeBuilders.get(getTestMethodName());
    }
}
