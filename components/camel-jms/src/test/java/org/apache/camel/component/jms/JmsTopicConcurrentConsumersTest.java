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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import jakarta.jms.ConnectionFactory;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.BindingQueryResult;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.artemis.common.ConnectionFactoryHelper;
import org.apache.camel.test.infra.artemis.services.AbstractArtemisEmbeddedService;
import org.apache.camel.test.infra.artemis.services.ArtemisService;
import org.apache.camel.test.infra.artemis.services.ServiceAware;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.apache.camel.test.infra.core.annotations.ContextFixture;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A consumer on a topic with more than one concurrent consumer and no shared subscription has one subscription per
 * consumer, so each message is processed once per consumer. The consumer logs a WARN about it when it starts.
 */
public class JmsTopicConcurrentConsumersTest extends AbstractJMSTest {

    private static final String PREFIX = "JmsTopicConcurrentConsumersTest.";

    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();

    private static final JmsConsumerLogCaptor CAPTOR = new JmsConsumerLogCaptor();

    protected CamelContext context;
    protected ProducerTemplate template;

    @BeforeAll
    static void captureLog() {
        ((Logger) LogManager.getLogger(JmsConsumer.class)).addAppender(CAPTOR);
    }

    @AfterAll
    static void stopCapture() {
        ((Logger) LogManager.getLogger(JmsConsumer.class)).removeAppender(CAPTOR);
    }

    @Test
    public void endpointConcurrentConsumersOnTopicProcessEachMessageOncePerConsumer() throws Exception {
        awaitSubscriptions(PREFIX + "endpoint", 3, 3);
        MockEndpoint mock = getMockEndpoint("mock:endpoint");
        mock.expectedBodiesReceived("x", "x", "x");
        mock.setAssertPeriod(500);

        template.sendBody("jms:topic:" + PREFIX + "endpoint", "x");

        MockEndpoint.assertIsSatisfied(context);
        assertWarning(PREFIX + "endpoint", "3 concurrent consumers");
    }

    @Test
    public void componentConcurrentConsumersApplyToTopicEndpoints() {
        awaitSubscriptions(PREFIX + "component", 3, 3);
        assertWarning(PREFIX + "component", "3 concurrent consumers");
    }

    @Test
    public void maxConcurrentConsumersOnTopic() {
        assertWarning(PREFIX + "max", "up to 4 concurrent consumers");
    }

    @Test
    public void simpleConsumerTypeOnTopic() {
        awaitSubscriptions(PREFIX + "simple", 2, 2);
        assertWarning(PREFIX + "simple", "2 concurrent consumers");
    }

    @Test
    public void singleConsumerOnTopic() throws Exception {
        awaitSubscriptions(PREFIX + "single", 1, 1);
        MockEndpoint mock = getMockEndpoint("mock:single");
        mock.expectedBodiesReceived("x");
        mock.setAssertPeriod(500);

        template.sendBody("jms:topic:" + PREFIX + "single", "x");

        MockEndpoint.assertIsSatisfied(context);
        assertNoWarning(PREFIX + "single");
    }

    @Test
    public void sharedSubscriptionWithThreeConsumers() throws Exception {
        // one shared subscription queue, with the 3 consumers of the listener container on it
        awaitSubscriptions(PREFIX + "shared", 1, 3);
        MockEndpoint mock = getMockEndpoint("mock:shared");
        mock.expectedBodiesReceived("x");
        mock.setAssertPeriod(500);

        template.sendBody("jms:topic:" + PREFIX + "shared", "x");

        MockEndpoint.assertIsSatisfied(context);
        assertNoWarning(PREFIX + "shared");
    }

    @Test
    public void queueWithThreeConsumers() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:queue");
        mock.expectedBodiesReceived("x");
        mock.setAssertPeriod(500);

        template.sendBody("jms:queue:" + PREFIX + "queue", "x");

        MockEndpoint.assertIsSatisfied(context);
        assertNoWarning(PREFIX + "queue");
    }

    @Override
    protected String getComponentName() {
        return "jms";
    }

    @ContextFixture
    @Override
    public void configureContext(CamelContext context) throws Exception {
        super.configureContext(context);
        // concurrentConsumers set on the component also applies to its topic endpoints
        ConnectionFactory cf = ConnectionFactoryHelper.createConnectionFactory(service);
        JmsComponent component = jmsComponentAutoAcknowledge(cf);
        component.setConcurrentConsumers(3);
        context.addComponent("jmsc", component);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("jms:topic:" + PREFIX + "endpoint?concurrentConsumers=3").to("mock:endpoint");
                from("jmsc:topic:" + PREFIX + "component").to("mock:component");
                from("jms:topic:" + PREFIX + "max?maxConcurrentConsumers=4").to("mock:max");
                from("jms:topic:" + PREFIX + "simple?consumerType=Simple&concurrentConsumers=2").to("mock:simple");
                from("jms:topic:" + PREFIX + "single").to("mock:single");
                from("jms:topic:" + PREFIX + "shared?concurrentConsumers=3&subscriptionShared=true"
                     + "&subscriptionName=JmsTopicConcurrentConsumersTest")
                        .to("mock:shared");
                from("jms:queue:" + PREFIX + "queue?concurrentConsumers=3").to("mock:queue");
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

    private static void awaitSubscriptions(String address, int queues, int consumers) {
        Awaitility.await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            ActiveMQServer server = broker();
            BindingQueryResult bindings = server.bindingQuery(SimpleString.of(address));
            assertEquals(queues, bindings.getQueueNames().size(), "subscription queues on " + address);
            int total = 0;
            for (SimpleString queue : bindings.getQueueNames()) {
                total += server.queueQuery(queue).getConsumerCount();
            }
            assertEquals(consumers, total, "consumers on " + address);
        });
    }

    private static ActiveMQServer broker() {
        @SuppressWarnings("unchecked")
        ArtemisService embedded = ((ServiceAware<ArtemisService>) service).getService();
        return ((AbstractArtemisEmbeddedService) embedded).getEmbeddedBrokerService().getActiveMQServer();
    }

    private static void assertWarning(String destination, String expected) {
        List<String> warnings = CAPTOR.warningsAbout(destination);
        assertEquals(1, warnings.size(), "WARN about " + destination + ": " + warnings);
        String warning = warnings.get(0);
        assertTrue(warning.contains(expected), warning);
        assertTrue(warning.contains("subscriptionShared=true"), warning);
    }

    private static void assertNoWarning(String destination) {
        // the consumer of the destination has started, so its WARN would already be logged
        List<String> warnings = CAPTOR.warningsAbout(destination);
        assertTrue(warnings.isEmpty(), "Expected no WARN about " + destination + ", got: " + warnings);
    }

    private static final class JmsConsumerLogCaptor extends AbstractAppender {
        private final List<LogEvent> events = new CopyOnWriteArrayList<>();

        JmsConsumerLogCaptor() {
            super("JmsTopicConcurrentConsumersTestCaptor", null, null, false, Property.EMPTY_ARRAY);
            start();
        }

        @Override
        public void append(LogEvent event) {
            events.add(event.toImmutable());
        }

        List<String> warningsAbout(String destination) {
            // other test classes may log in parallel: keep the lines that name the destination
            return events.stream()
                    .filter(e -> e.getLevel().isMoreSpecificThan(Level.WARN))
                    .map(e -> e.getMessage().getFormattedMessage())
                    .filter(m -> m.contains(destination + " "))
                    .toList();
        }
    }
}
