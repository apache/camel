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

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.jms.ConnectionFactory;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentClientAcknowledge;

/**
 * @version $Revision$
 */
public class JmsRouteRequestReplyTest extends ContextTestSupport {
    protected static String componentName = "amq";
    protected static String componentName1 = "amq1";
    protected static String endpoingUriA = componentName + ":queue:test.a";
    protected static String endpointUriB = componentName + ":queue:test.b";
    protected static String endpointUriB1 = componentName1 + ":queue:test.b";
    protected static String request = "Hello World";
    protected static String expectedReply = "Re: " + request;
    protected static int maxTasks = 100;
    protected static int maxServerTasks = maxTasks / 5;
    protected static int maxCalls = 10;
    protected static AtomicBoolean inited = new AtomicBoolean(false);
    protected static Map<String, ContextBuilder> contextBuilders = new HashMap<String, ContextBuilder>();
    protected static Map<String, RouteBuilder> routeBuilders = new HashMap<String, RouteBuilder>();

    private interface ContextBuilder {
        CamelContext buildContext(CamelContext context) throws Exception;
    }

    public static class SingleNodeDeadEndRouteBuilder extends RouteBuilder {
        public void configure() throws Exception {
            from(endpoingUriA).process(new Processor() {
                public void process(Exchange e) {
                    // do nothing
                }
            });
        }
    };

    public static class SingleNodeRouteBuilder extends RouteBuilder {
        public void configure() throws Exception {
            from(endpoingUriA).process(new Processor() {
                public void process(Exchange e) {
                    String request = e.getIn().getBody(String.class);
                    e.getOut(true).setBody(expectedReply + request.substring(request.indexOf('-')));
                }
            });
        }
    };

    public static class MultiNodeRouteBuilder extends RouteBuilder {
        public void configure() throws Exception {
            from(endpoingUriA).to(endpointUriB);
            from(endpointUriB).process(new Processor() {
                public void process(Exchange e) {
                    String request = e.getIn().getBody(String.class);
                    e.getOut(true).setBody(expectedReply + request.substring(request.indexOf('-')));
                }
            });
        }
    };

    public static class MultiNodeDiffCompRouteBuilder extends RouteBuilder {
        public void configure() throws Exception {
            from(endpoingUriA).to(endpointUriB1);
            from(endpointUriB1).process(new Processor() {
                public void process(Exchange e) {
                    String request = e.getIn().getBody(String.class);
                    e.getOut(true).setBody(expectedReply + request.substring(request.indexOf('-')));
                }
            });
        }
    };

    protected static void init() {
        if (inited.compareAndSet(false, true)) {

            ContextBuilder contextBuilderMessageID = new ContextBuilder() {
                public CamelContext buildContext(CamelContext context) throws Exception {
                    ConnectionFactory connectionFactory =
                        new ActiveMQConnectionFactory("vm://localhost?broker.persistent=false");
                    JmsComponent jmsComponent = jmsComponentClientAcknowledge(connectionFactory);
                    jmsComponent.setUseMessageIDAsCorrelationID(true);
                    jmsComponent.setConcurrentConsumers(maxServerTasks);
                    context.addComponent(componentName, jmsComponent);
                    return context;
                }
            };

            ContextBuilder contextBuilderCorrelationID = new ContextBuilder() {
                public CamelContext buildContext(CamelContext context) throws Exception {
                    ConnectionFactory connectionFactory =
                        new ActiveMQConnectionFactory("vm://localhost?broker.persistent=false");
                    JmsComponent jmsComponent = jmsComponentClientAcknowledge(connectionFactory);
                    jmsComponent.setUseMessageIDAsCorrelationID(false);
                    jmsComponent.setConcurrentConsumers(maxServerTasks);
                    context.addComponent(componentName, jmsComponent);
                    return context;
                }
            };

            ContextBuilder contextBuilderCorrelationIDDiffComp = new ContextBuilder() {
                public CamelContext buildContext(CamelContext context) throws Exception {
                    ConnectionFactory connectionFactory =
                        new ActiveMQConnectionFactory("vm://localhost?broker.persistent=false");
                    JmsComponent jmsComponent = jmsComponentClientAcknowledge(connectionFactory);
                    jmsComponent.setUseMessageIDAsCorrelationID(false);
                    jmsComponent.setConcurrentConsumers(maxServerTasks);
                    context.addComponent(componentName, jmsComponent);
                    jmsComponent = jmsComponentClientAcknowledge(connectionFactory);
                    jmsComponent.setUseMessageIDAsCorrelationID(false);
                    jmsComponent.setConcurrentConsumers(maxServerTasks);
                    context.addComponent(componentName1, jmsComponent);
                    return context;
                }
            };


            contextBuilders.put("testUseMessageIDAsCorrelationID", contextBuilderMessageID);
            contextBuilders.put("testUseCorrelationID", contextBuilderCorrelationID);
            contextBuilders.put("testUseMessageIDAsCorrelationIDMultiNode", contextBuilderMessageID);
            contextBuilders.put("testUseCorrelationIDMultiNode", contextBuilderCorrelationID);
            contextBuilders.put("testUseCorrelationIDMultiNodeDiffComponents", contextBuilderCorrelationIDDiffComp);
            contextBuilders.put("testUseMessageIDAsCorrelationIDTimeout", contextBuilderMessageID);
            contextBuilders.put("testUseCorrelationIDTimeout", contextBuilderMessageID);

            routeBuilders.put("testUseMessageIDAsCorrelationID", new SingleNodeRouteBuilder());
            routeBuilders.put("testUseCorrelationID", new SingleNodeRouteBuilder());
            routeBuilders.put("testUseMessageIDAsCorrelationIDMultiNode", new MultiNodeRouteBuilder());
            routeBuilders.put("testUseCorrelationIDMultiNode", new MultiNodeRouteBuilder());
            routeBuilders.put("testUseCorrelationIDMultiNodeDiffComponents", new MultiNodeDiffCompRouteBuilder());
            routeBuilders.put("testUseMessageIDAsCorrelationIDTimeout", new SingleNodeDeadEndRouteBuilder());
            routeBuilders.put("testUseCorrelationIDTimeout", new SingleNodeDeadEndRouteBuilder());
        }
    }

    public class Task extends Thread {
        private AtomicInteger counter;
        private boolean ok = true;
        private String message = "";

        public Task(AtomicInteger counter) {
            this.counter = counter;
        }

        public void run() {
            for (int i = 0; i < maxCalls; i++) {
                int callId = counter.incrementAndGet();
                Object reply = template.requestBody(endpoingUriA, request + "-" + callId);
                if (!reply.equals(expectedReply + "-" + callId)) {
                    ok = false;
                    message = "Unexpected reply. Expected: '" + expectedReply  + "-" + callId
                              + "'; Received: '" +  reply + "'";
                }
            }
        }
        public void assertSuccess() {
            assertTrue(message, ok);
        }
    }

    @Override
    protected void setUp() throws Exception {
        init();
        super.setUp();
    }

    public void testUseMessageIDAsCorrelationID() throws Exception {
        runRequestReplyThreaded();
    }

    public void testUseCorrelationID() throws Exception {
        runRequestReplyThreaded();
    }

    public void testUseMessageIDAsCorrelationIDMultiNode() throws Exception {
        runRequestReplyThreaded();
    }

    public void testUseCorrelationIDTimeout() throws Exception {
        Object reply = template.requestBody(endpoingUriA, request);
        assertEquals(reply, request);
        JmsComponent c = (JmsComponent)context.getComponent(componentName);
        // Wait 1 extra purge cycle to make sure that TimeoutMap had a chance to cleanup
        Thread.sleep(c.getConfiguration().getRequestMapPurgePollTimeMillis());
        assertTrue(c.getRequestor().getRequestMap().size() == 0);
    }

    public void testUseMessageIDAsCorrelationIDTimeout() throws Exception {
        JmsComponent c = (JmsComponent)context.getComponent(componentName);
        Object reply = template.requestBody(endpoingUriA, request);
        assertEquals(reply, request);
        // Wait 1 extra purge cycle to make sure that TimeoutMap had a chance to cleanup
        Thread.sleep(c.getConfiguration().getRequestMapPurgePollTimeMillis());
        assertTrue(c.getRequestor().getDeferredRequestMap().size() == 0);
    }

    public void testUseCorrelationIDMultiNodeDiffComponents() throws Exception {
        runRequestReplyThreaded();
    }

    /*
     * REVISIT: This currently fails because there is a single instance of Requestor per JmsComponent
     * which shares requestMap amongst JmsProducers. This is a problem in case where the same correlationID
     * value travels between nodes serviced by the same JmsComponent:
     * client -> producer1 -> corrId -> consumer1 -> producer2 -> corrId -> consumer
     * producer1 (Bum! @) <- corrId <- consumer1 <- producer2 <- corrId <- reply
     *
     * @ - The request entry for corrId was already removed from JmsProducer shared requestMap
     *
     * Possible ways to solve this: Each JmsProducer gets its own replyTo destination
     *

        public void testUseCorrelationIDMultiNode() throws Exception {
            runRequestReplyThreaded();
        }
    */

    protected void runRequestReplyThreaded() throws Exception {
        final AtomicInteger counter = new AtomicInteger(-1);
        Task[] tasks = new Task[maxTasks];
        for (int i = 0; i < maxTasks; ++i) {
            Task task = new Task(counter);
            tasks[i] = task;
            task.start();
        }
        for (int i = 0; i < maxTasks; ++i) {
            tasks[i].join();
            tasks[i].assertSuccess();
        }
    }

    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        return contextBuilders.get(getName()).buildContext(camelContext);
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return routeBuilders.get(getName());
    }
}
