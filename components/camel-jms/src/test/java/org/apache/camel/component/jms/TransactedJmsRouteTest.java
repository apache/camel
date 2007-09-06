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
package org.apache.camel.component.jms;

import java.util.concurrent.TimeUnit;

import javax.jms.ConnectionFactory;
import javax.jms.Session;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.DelegateProcessor;
import org.apache.camel.spi.Policy;
import org.apache.camel.spring.SpringCamelContext;
import org.apache.camel.spring.SpringRouteBuilder;
import org.apache.camel.spring.spi.SpringTransactionPolicy;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.apache.camel.component.mock.MockEndpoint.assertIsSatisfied;
import static org.apache.camel.component.mock.MockEndpoint.assertWait;

/**
 * @version $Revision: 529902 $
 */
public class TransactedJmsRouteTest extends ContextTestSupport {

    private static final transient Log LOG = LogFactory.getLog(TransactedJmsRouteTest.class);
    private MockEndpoint mockEndpointA;
    private MockEndpoint mockEndpointB;
    private ClassPathXmlApplicationContext spring;
    private MockEndpoint mockEndpointC;
    private MockEndpoint mockEndpointD;
    protected int assertTimeoutSeconds = 10;

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new SpringRouteBuilder() {
            public void configure() {

                Policy requried = new SpringTransactionPolicy(bean(TransactionTemplate.class, "PROPAGATION_REQUIRED"));
                Policy notsupported = new SpringTransactionPolicy(bean(TransactionTemplate.class, "PROPAGATION_NOT_SUPPORTED"));
                Policy requirenew = new SpringTransactionPolicy(bean(TransactionTemplate.class, "PROPAGATION_REQUIRES_NEW"));

                Policy rollback = new Policy() {
                    public Processor wrap(Processor processor) {
                        return new DelegateProcessor(processor) {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                processNext(exchange);
                                throw new RuntimeException("rollback");
                            }

                            @Override
                            public String toString() {
                                return "rollback(" + getProcessor() + ")";
                            }
                        };
                    }
                };

                Policy catchRollback = new Policy() {
                    public Processor wrap(Processor processor) {
                        return new DelegateProcessor(processor) {
                            @Override
                            public void process(Exchange exchange) {
                                try {
                                    processNext(exchange);
                                } catch (Throwable ignore) {
                                }
                            }

                            @Override
                            public String toString() {
                                return "catchRollback(" + getProcessor() + ")";
                            }
                        };
                    }
                };

                // NOTE: ErrorHandler has to be disabled since it operates
                // within the failed transaction.
                inheritErrorHandler(false);
                // Used to validate messages are sent to the target.
                from("activemq:queue:mock.a").trace().to("mock:a");
                from("activemq:queue:mock.b").trace().to("mock:b");
                from("activemq:queue:mock.c").trace().to("mock:c");
                from("activemq:queue:mock.d").trace().to("mock:d");

                // Receive from a and send to target in 1 tx.
                from("activemq:queue:a").to("activemq:queue:mock.a");

                // Cause an error after processing the send. The send to
                // activemq:queue:mock.a should rollback
                // since it is participating in the inbound transaction, but
                // mock:b does not participate so we should see the message get
                // there. Also, expect multiple inbound retries as the message
                // is rolled back.
                // transactionPolicy(requried);
                from("activemq:queue:b").policy(rollback).to("activemq:queue:mock.a", "mock:b");

                // Cause an error after processing the send in a new
                // transaction. The send to activemq:queue:mock.a should
                // rollback
                // since the rollback is within it's transaction, but mock:b
                // does not participate so we should see the message get
                // there. Also, expect the message to be successfully consumed
                // since the rollback error is not propagated.
                // transactionPolicy(requried);
                from("activemq:queue:c").policy(catchRollback).policy(requirenew).policy(rollback).to("activemq:queue:mock.a", "mock:b");

                // Cause an error after processing the send in without a
                // transaction. The send to activemq:queue:mock.a should
                // succeed.
                // Also, expect the message to be successfully consumed since
                // the rollback error is not propagated.
                from("activemq:queue:d").policy(catchRollback).policy(notsupported).policy(rollback).to("activemq:queue:mock.a");

                // Receive message on a non transacted JMS endpoint, start a
                // transaction, send and then rollback.
                // mock:a should never get the message (due to rollback) but
                // mock:b should get only 1 since the
                // inbound was not transacted.
                JmsEndpoint endpoint = (JmsEndpoint)endpoint("activemq:queue:e");
                endpoint.getConfiguration().setTransacted(false);
                endpoint.getConfiguration().setAcknowledgementMode(Session.AUTO_ACKNOWLEDGE);
                from(endpoint).policy(requried).policy(rollback).to("activemq:queue:mock.a", "mock:b");

                //
                // Sets up 2 consumers on single topic, one being transacted the
                // other not. Used to verify
                // That each consumer can have independently configured
                // transaction settings.
                // Do a rollback, should cause the transacted consumer to
                // re-deliver (since we are using a durable subscription) but
                // not the un-transacted one.
                // TODO: find out why re-delivery is not working with a non
                // durable transacted topic.
                JmsEndpoint endpoint1 = (JmsEndpoint)endpoint("activemq:topic:f");
                endpoint1.getConfiguration().setTransacted(true);
                endpoint1.getConfiguration().setSubscriptionDurable(true);
                endpoint1.getConfiguration().setClientId("client2");
                endpoint1.getConfiguration().setDurableSubscriptionName("sub");
                from(endpoint1).policy(requried).policy(rollback).to("activemq:queue:mock.a", "mock:b");

                JmsEndpoint endpoint2 = (JmsEndpoint)endpoint("activemq:topic:f");
                endpoint2.getConfiguration().setTransacted(false);
                endpoint2.getConfiguration().setAcknowledgementMode(Session.AUTO_ACKNOWLEDGE);
                endpoint2.getConfiguration().setSubscriptionDurable(true);
                endpoint2.getConfiguration().setClientId("client1");
                endpoint2.getConfiguration().setDurableSubscriptionName("sub");
                from(endpoint2).policy(requried).policy(rollback).to("activemq:queue:mock.c", "mock:d");
            }
        };
    }

    protected CamelContext createCamelContext() throws Exception {
        spring = new ClassPathXmlApplicationContext("org/apache/camel/component/jms/spring.xml");
        SpringCamelContext ctx = SpringCamelContext.springCamelContext(spring);
        PlatformTransactionManager transactionManager = (PlatformTransactionManager)spring.getBean("jmsTransactionManager");
        ConnectionFactory connectionFactory = (ConnectionFactory)spring.getBean("jmsConnectionFactory");
        JmsComponent component = JmsComponent.jmsComponentTransacted(connectionFactory, transactionManager);
        component.getConfiguration().setConcurrentConsumers(1);
        ctx.addComponent("activemq", component);
        return ctx;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        // for (Route route : this.context.getRoutes()) {
        // System.out.println(route);
        // }

        mockEndpointA = getMockEndpoint("mock:a");
        mockEndpointB = getMockEndpoint("mock:b");
        mockEndpointC = getMockEndpoint("mock:c");
        mockEndpointD = getMockEndpoint("mock:d");
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        spring.destroy();
    }

    /**
     * This test seems to be fail every other run.
     * 
     * @throws Exception
     */
    public void disabledtestSenarioF() throws Exception {
        String expected = getName() + ": " + System.currentTimeMillis();
        mockEndpointA.expectedMessageCount(0);
        mockEndpointB.expectedMinimumMessageCount(2);
        mockEndpointC.expectedMessageCount(0);
        mockEndpointD.expectedMessageCount(1);
        sendBody("activemq:topic:f", expected);

        // Wait till the endpoints get their messages.
        assertWait(10, TimeUnit.SECONDS, mockEndpointA, mockEndpointB, mockEndpointC, mockEndpointD);

        // Wait a little more to make sure extra messages are not received.
        Thread.sleep(1000);

        assertIsSatisfied(mockEndpointA, mockEndpointB, mockEndpointC, mockEndpointD);
    }

    public void testSenarioA() throws Exception {
        String expected = getName() + ": " + System.currentTimeMillis();
        mockEndpointA.expectedBodiesReceived(expected);
        sendBody("activemq:queue:a", expected);
        assertIsSatisfied(mockEndpointA);
    }

    public void TODO_testSenarioB() throws Exception {
        String expected = getName() + ": " + System.currentTimeMillis();
        mockEndpointA.expectedMessageCount(0);
        mockEndpointB.expectedMinimumMessageCount(2); // May be more since
                                                        // spring seems to go
                                                        // into tight loop
                                                        // re-delivering.
        sendBody("activemq:queue:b", expected);
        assertIsSatisfied(assertTimeoutSeconds, TimeUnit.SECONDS, mockEndpointA, mockEndpointB);
    }

    public void testSenarioC() throws Exception {
        String expected = getName() + ": " + System.currentTimeMillis();
        mockEndpointA.expectedMessageCount(0);
        mockEndpointB.expectedMessageCount(1); // Should only get 1 message the
                                                // incoming transaction does not
                                                // rollback.
        sendBody("activemq:queue:c", expected);

        // Wait till the endpoints get their messages.
        assertWait(assertTimeoutSeconds, TimeUnit.SECONDS, mockEndpointA, mockEndpointB);

        // Wait a little more to make sure extra messages are not received.
        Thread.sleep(1000);

        assertIsSatisfied(mockEndpointA, mockEndpointB);
    }

    public void testSenarioD() throws Exception {
        String expected = getName() + ": " + System.currentTimeMillis();
        mockEndpointA.expectedMessageCount(1);
        sendBody("activemq:queue:d", expected);

        // Wait till the endpoints get their messages.
        assertWait(assertTimeoutSeconds, TimeUnit.SECONDS, mockEndpointA, mockEndpointB);

        // Wait a little more to make sure extra messages are not received.
        Thread.sleep(1000);

        assertIsSatisfied(mockEndpointA);
    }

    public void testSenarioE() throws Exception {
        String expected = getName() + ": " + System.currentTimeMillis();
        mockEndpointA.expectedMessageCount(0);
        mockEndpointB.expectedMessageCount(1);
        sendBody("activemq:queue:e", expected);

        // Wait till the endpoints get their messages.
        assertWait(5, TimeUnit.SECONDS, mockEndpointA, mockEndpointB);

        // Wait a little more to make sure extra messages are not received.
        Thread.sleep(1000);

        assertIsSatisfied(mockEndpointA, mockEndpointB);
    }

}
