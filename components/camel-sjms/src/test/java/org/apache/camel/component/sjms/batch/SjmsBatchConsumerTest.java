/**
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
package org.apache.camel.component.sjms.batch;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import javax.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.sjms.SjmsComponent;
import org.apache.camel.component.sjms.support.MockConnectionFactory;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.SimpleRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.StopWatch;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SjmsBatchConsumerTest extends CamelTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(SjmsBatchConsumerTest.class);

    @Rule
    public EmbeddedActiveMQBroker broker = new EmbeddedActiveMQBroker("localhost");

    @Override
    public CamelContext createCamelContext() throws Exception {
        SimpleRegistry registry = new SimpleRegistry();
        registry.put("testStrategy", new ListAggregationStrategy());
        // the only thing special about this MockConnectionFactor is it allows us to call returnBadSessionNTimes(int)
        // which will cause the MockSession to throw an IllegalStateException <int> times before returning a valid one.
        // This gives us the ability to test bad sessions
        ConnectionFactory connectionFactory = new MockConnectionFactory(broker.getTcpConnectorUri());

        SjmsComponent sjmsComponent = new SjmsComponent();
        sjmsComponent.setConnectionFactory(connectionFactory);

        SjmsBatchComponent sjmsBatchComponent = new SjmsBatchComponent();
        sjmsBatchComponent.setConnectionFactory(connectionFactory);

        CamelContext context = new DefaultCamelContext(registry);
        context.addComponent("sjms", sjmsComponent);
        context.addComponent("sjms-batch", sjmsBatchComponent);
        return context;
    }

    private static class TransactedSendHarness extends RouteBuilder {
        private final String queueName;

        TransactedSendHarness(String queueName) {
            this.queueName = queueName;
        }

        @Override
        public void configure() throws Exception {
            from("direct:in").routeId("harness").startupOrder(20)
                    .split(body())
                    .toF("sjms:queue:%s?transacted=true", queueName)
                    .to("mock:before")
                    .end();
        }
    }

    @Override
    public boolean isUseAdviceWith() {
        return true;
    }

    @Test
    public void testConsumption() throws Exception {

        final int messageCount = 10000;
        final int consumerCount = 5;

        final String queueName = getQueueName();
        context.addRoutes(new TransactedSendHarness(queueName));
        context.addRoutes(new RouteBuilder() {
            public void configure() throws Exception {

                int completionTimeout = 1000;
                int completionSize = 200;

                fromF("sjms-batch:%s?completionTimeout=%s&completionSize=%s&consumerCount=%s&aggregationStrategy=#testStrategy",
                        queueName, completionTimeout, completionSize, consumerCount)
                        .routeId("batchConsumer").startupOrder(10).autoStartup(false)
                        .split(body())
                        .to("mock:split");
            }
        });
        context.start();

        MockEndpoint mockBefore = getMockEndpoint("mock:before");
        mockBefore.setExpectedMessageCount(messageCount);

        MockEndpoint mockSplit = getMockEndpoint("mock:split");
        mockSplit.setExpectedMessageCount(messageCount);

        LOG.info("Sending messages");
        template.sendBody("direct:in", generateStrings(messageCount));
        LOG.info("Send complete");

        StopWatch stopWatch = new StopWatch();
        context.startRoute("batchConsumer");
        assertMockEndpointsSatisfied();
        long time = stopWatch.stop();

        LOG.info("Processed {} messages in {} ms", messageCount, time);
        LOG.info("Average throughput {} msg/s", (long) (messageCount / (time / 1000d)));
    }

    @Test
    public void testConsumptionCompletionSize() throws Exception {
        final int completionSize = 5;
        final int completionTimeout = -1; // size-based only

        final String queueName = getQueueName();
        context.addRoutes(new TransactedSendHarness(queueName));
        context.addRoutes(new RouteBuilder() {
            public void configure() throws Exception {
                fromF("sjms-batch:%s?completionTimeout=%s&completionSize=%s&aggregationStrategy=#testStrategy",
                        queueName, completionTimeout, completionSize).routeId("batchConsumer").startupOrder(10)
                        .log(LoggingLevel.DEBUG, "${body.size}")
                        .to("mock:batches");
            }
        });
        context.start();

        int messageCount = 100;
        MockEndpoint mockBatches = getMockEndpoint("mock:batches");
        mockBatches.expectedMessageCount(messageCount / completionSize);

        template.sendBody("direct:in", generateStrings(messageCount));
        mockBatches.assertIsSatisfied();
    }

    @Test
    public void testConsumptionCompletionPredicate() throws Exception {
        final String completionPredicate = "${body} contains 'done'";
        final int completionTimeout = -1; // predicate-based only

        final String queueName = getQueueName();
        context.addRoutes(new TransactedSendHarness(queueName));
        context.addRoutes(new RouteBuilder() {
            public void configure() throws Exception {
                fromF("sjms-batch:%s?completionTimeout=%s&completionPredicate=%s&aggregationStrategy=#testStrategy&eagerCheckCompletion=true",
                        queueName, completionTimeout, completionPredicate).routeId("batchConsumer").startupOrder(10)
                        .log(LoggingLevel.DEBUG, "${body.size}")
                        .to("mock:batches");
            }
        });
        context.start();

        MockEndpoint mockBatches = getMockEndpoint("mock:batches");
        mockBatches.expectedMessageCount(2);

        template.sendBody("direct:in", generateStrings(50));
        template.sendBody("direct:in", "Message done");
        template.sendBody("direct:in", generateStrings(50));
        template.sendBody("direct:in", "Message done");
        mockBatches.assertIsSatisfied();
    }

    @Test
    public void testConsumptionCompletionTimeout() throws Exception {
        final int completionTimeout = 2000;
        final int completionSize = -1; // timeout-based only

        final String queueName = getQueueName();
        context.addRoutes(new TransactedSendHarness(queueName));
        context.addRoutes(new RouteBuilder() {
            public void configure() throws Exception {
                fromF("sjms-batch:%s?completionTimeout=%s&completionSize=%s&aggregationStrategy=#testStrategy",
                        queueName, completionTimeout, completionSize).routeId("batchConsumer").startupOrder(10)
                        .to("mock:batches");
            }
        });
        context.start();

        int messageCount = 50;
        assertTrue(messageCount < SjmsBatchEndpoint.DEFAULT_COMPLETION_SIZE);
        MockEndpoint mockBatches = getMockEndpoint("mock:batches");
        mockBatches.expectedMessageCount(1);  // everything batched together

        template.sendBody("direct:in", generateStrings(messageCount));
        mockBatches.assertIsSatisfied();
        assertFirstMessageBodyOfLength(mockBatches, messageCount);
    }

    @Test
    public void testConsumptionCompletionInterval() throws Exception {
        final int completionInterval = 2000;
        final int completionSize = -1; // timeout-based only

        final String queueName = getQueueName();
        context.addRoutes(new TransactedSendHarness(queueName));
        context.addRoutes(new RouteBuilder() {
            public void configure() throws Exception {
                fromF("sjms-batch:%s?completionInterval=%s&completionSize=%s&aggregationStrategy=#testStrategy",
                        queueName, completionInterval, completionSize).routeId("batchConsumer").startupOrder(10)
                        .to("mock:batches");
            }
        });
        context.start();

        int messageCount = 50;
        assertTrue(messageCount < SjmsBatchEndpoint.DEFAULT_COMPLETION_SIZE);

        MockEndpoint mockBatches = getMockEndpoint("mock:batches");
        mockBatches.expectedMinimumMessageCount(1);  // everything ought to be batched together but the interval may trigger in between and we get 2 etc

        template.sendBody("direct:in", generateStrings(messageCount));

        mockBatches.assertIsSatisfied();
    }

    @Test
    public void testConsumptionSendEmptyMessageWhenIdle() throws Exception {
        final int completionInterval = 2000;
        final int completionSize = -1; // timeout-based only

        final String queueName = getQueueName();
        context.addRoutes(new TransactedSendHarness(queueName));
        context.addRoutes(new RouteBuilder() {
            public void configure() throws Exception {
                fromF("sjms-batch:%s?completionInterval=%s&completionSize=%s&sendEmptyMessageWhenIdle=true&aggregationStrategy=#testStrategy",
                        queueName, completionInterval, completionSize).routeId("batchConsumer").startupOrder(10)
                        .to("mock:batches");
            }
        });
        context.start();

        int messageCount = 50;
        assertTrue(messageCount < SjmsBatchEndpoint.DEFAULT_COMPLETION_SIZE);

        MockEndpoint mockBatches = getMockEndpoint("mock:batches");
        // trigger a couple of empty messages
        mockBatches.expectedMinimumMessageCount(3);

        template.sendBody("direct:in", generateStrings(messageCount));

        mockBatches.assertIsSatisfied();
    }

    /**
     * Checks whether multiple consumer endpoints can operate in parallel.
     */
    @Test
    public void testConsumptionMultipleConsumerEndpoints() throws Exception {
        final int completionTimeout = 2000;
        final int completionSize = 5;

        final String queueName = getQueueName();
        context.addRoutes(new RouteBuilder() {
            public void configure() throws Exception {

                from("direct:in")
                    .split().body()
                        .multicast()
                            .toF("sjms:%s", queueName + "A")
                            .toF("sjms:%s", queueName + "B")
                        .end();

                fromF("sjms-batch:%s?completionTimeout=%s&completionSize=%s&aggregationStrategy=#testStrategy",
                        queueName + "A", completionTimeout, completionSize).routeId("batchConsumerA")
                        .to("mock:outA");

                fromF("sjms-batch:%s?completionTimeout=%s&completionSize=%s&aggregationStrategy=#testStrategy",
                        queueName + "B", completionTimeout, completionSize).routeId("batchConsumerB")
                        .to("mock:outB");

            }
        });
        context.start();

        int messageCount = 5;

        assertTrue(messageCount < SjmsBatchEndpoint.DEFAULT_COMPLETION_SIZE);
        MockEndpoint mockOutA = getMockEndpoint("mock:outA");
        mockOutA.expectedMessageCount(1);  // everything batched together
        MockEndpoint mockOutB = getMockEndpoint("mock:outB");
        mockOutB.expectedMessageCount(1);  // everything batched together

        template.sendBody("direct:in", generateStrings(messageCount));
        assertMockEndpointsSatisfied();

        assertFirstMessageBodyOfLength(mockOutA, messageCount);
        assertFirstMessageBodyOfLength(mockOutB, messageCount);
    }

    @Test
    public void testConsumptionRollback() throws Exception {
        final int completionTimeout = 2000;
        final int completionSize = 5;

        final String queueName = getQueueName();
        context.addRoutes(new TransactedSendHarness(queueName));
        context.addRoutes(new RouteBuilder() {
            public void configure() throws Exception {
                fromF("sjms-batch:%s?completionTimeout=%s&completionSize=%s&aggregationStrategy=#testStrategy",
                        queueName, completionTimeout, completionSize).routeId("batchConsumer").startupOrder(10)
                        .to("mock:batches");
            }
        });
        context.start();

        int messageCount = 5;
        MockEndpoint mockBatches = getMockEndpoint("mock:batches");
        // the first time around, the batch should throw an exception
        mockBatches.whenExchangeReceived(1, new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                throw new RuntimeException("Boom!");
            }
        });
        // so the batch should be processed twice due to redelivery
        mockBatches.expectedMessageCount(2);

        template.sendBody("direct:in", generateStrings(messageCount));
        mockBatches.assertIsSatisfied();

    }

    @Test
    public void testConsumptionBadSession() throws Exception {

        final int messageCount = 5;
        final int consumerCount = 1;
        SjmsBatchComponent sb = (SjmsBatchComponent)context.getComponent("sjms-batch");
        MockConnectionFactory cf = (MockConnectionFactory)sb.getConnectionFactory();
        cf.returnBadSessionNTimes(2);

        final String queueName = getQueueName();
        context.addRoutes(new TransactedSendHarness(queueName));
        context.addRoutes(new RouteBuilder() {
            public void configure() throws Exception {

                int completionTimeout = 1000;
                int completionSize = 200;

                // keepAliveDelay=300 is the key... it's a 300 millis delay between attempts to create a new session.
                fromF("sjms-batch:%s?completionTimeout=%s&completionSize=%s&consumerCount=%s&aggregationStrategy=#testStrategy&keepAliveDelay=300",
                        queueName, completionTimeout, completionSize, consumerCount)
                        .routeId("batchConsumer").startupOrder(10).autoStartup(false)
                        .split(body())
                        .to("mock:split");
            }
        });
        context.start();

        MockEndpoint mockBefore = getMockEndpoint("mock:before");
        mockBefore.setExpectedMessageCount(messageCount);

        MockEndpoint mockSplit = getMockEndpoint("mock:split");
        mockSplit.setExpectedMessageCount(messageCount);

        LOG.info("Sending messages");
        template.sendBody("direct:in", generateStrings(messageCount));
        LOG.info("Send complete");

        StopWatch stopWatch = new StopWatch();
        context.startRoute("batchConsumer");

        assertMockEndpointsSatisfied();
        long time = stopWatch.stop();

    }

    private void assertFirstMessageBodyOfLength(MockEndpoint mockEndpoint, int expectedLength) {
        Exchange exchange = mockEndpoint.getExchanges().get(0);
        assertEquals(expectedLength, exchange.getIn().getBody(List.class).size());
    }

    private String getQueueName() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyMMddhhmmss");
        return "sjms-batch-" + sdf.format(new Date());
    }

    private String[] generateStrings(int messageCount) {
        String[] strings = new String[messageCount];
        for (int i = 0; i < messageCount; i++) {
            strings[i] = "message:" + i;
        }
        return strings;
    }

}
