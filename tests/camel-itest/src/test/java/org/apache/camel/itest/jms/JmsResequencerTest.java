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
package org.apache.camel.itest.jms;

import java.util.List;

import javax.jms.ConnectionFactory;

import org.apache.camel.Body;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.itest.CamelJmsTestHelper;
import org.apache.camel.model.config.BatchResequencerConfig;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

public class JmsResequencerTest extends CamelTestSupport  {
    
    private static final Logger LOG = LoggerFactory.getLogger(JmsResequencerTest.class);
    private ReusableBean b1 = new ReusableBean("myBean1");
    private ReusableBean b2 = new ReusableBean("myBean2");
    private ReusableBean b3 = new ReusableBean("myBean3");
    
        
    private MockEndpoint resultEndpoint;

    public void sendBodyAndHeader(String endpointUri, final Object body, final String headerName,
                                  final Object headerValue) {
        template.send(endpointUri, new Processor() {
            public void process(Exchange exchange) {
                Message in = exchange.getIn();
                in.setBody(body);
                in.setHeader(headerName, headerValue);
                //in.setHeader("testCase", getName());
                in.setHeader(Exchange.BEAN_METHOD_NAME, "execute");
            }
        });
    }
    
    @Test
    public void testSendMessagesInWrongOrderButReceiveThemInCorrectOrder() throws Exception {              
        sendAndVerifyMessages("activemq:queue:batch");
    }
    
    @Test
    public void testSendMessageToStream() throws Exception {
        sendAndVerifyMessages("activemq:queue:stream");
    }
        
    private void sendAndVerifyMessages(String endpointUri) throws Exception {
        resultEndpoint.expectedBodiesReceived("msg1", "msg2", "msg3", "msg4", "msg5", "msg6");
        sendBodyAndHeader(endpointUri, "msg4", "seqnum", 4L);
        sendBodyAndHeader(endpointUri, "msg1", "seqnum", 1L);
        sendBodyAndHeader(endpointUri, "msg3", "seqnum", 3L);
        sendBodyAndHeader(endpointUri, "msg2", "seqnum", 2L);
        sendBodyAndHeader(endpointUri, "msg6", "seqnum", 6L);
        sendBodyAndHeader(endpointUri, "msg5", "seqnum", 5L);
        resultEndpoint.assertIsSatisfied();
        List<Exchange> list = resultEndpoint.getReceivedExchanges();
        for (Exchange exchange : list) {
            log.debug("Received: " + exchange);
        }
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        
        resultEndpoint = getMockEndpoint("mock:result");
        
        Object lookedUpBean = context.getRegistry().lookupByName("myBean1");
        assertSame("Lookup of 'myBean' should return same object!", b1, lookedUpBean);
        lookedUpBean = context.getRegistry().lookupByName("myBean2");
        assertSame("Lookup of 'myBean' should return same object!", b2, lookedUpBean);
        lookedUpBean = context.getRegistry().lookupByName("myBean3");
        assertSame("Lookup of 'myBean' should return same object!", b3, lookedUpBean);
       
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {

                from("activemq:queue:batch")
                    .to(callExecuteOnBean("myBean1"))
                    .resequence(header("seqnum"))
                    .batch(new BatchResequencerConfig(100, 2000L))
                    .to(callExecuteOnBean("myBean2"))
                    .to("activemq:queue:stop");
                
                from("activemq:queue:stream")
                    .to(callExecuteOnBean("myBean1"))
                    .resequence(header("seqnum"))
                    .stream()
                    .to(callExecuteOnBean("myBean2"))
                    .to("activemq:queue:stop");

                from("activemq:queue:stop")
                    .to(callExecuteOnBean("myBean3"))
                    .to("mock:result");

            }
        };
    }
    
    private static String callExecuteOnBean(String beanName) {
        return "bean:" + beanName + "?method=execute";
    }
    
    public class ReusableBean {
        public String body;
        private String name;       

        public ReusableBean(String name) {
            this.name = name;
        }

        public void setName(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return "MyBean:" + name;
        }

        public void read(@Body String body) {
            this.body = body;
            LOG.info(name + " read() method on " + this + " with body: " + body);
        }

        public void execute() {
            LOG.info(name + " started");
            LOG.info(name + " finished");
        }
    }

    @Override
    protected void bindToRegistry(Registry registry) throws Exception {
        // add ActiveMQ with embedded broker
        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
        JmsComponent amq = jmsComponentAutoAcknowledge(connectionFactory);
        amq.setCamelContext(context);
        registry.bind("activemq", amq);

        registry.bind("myBean1", b1);
        registry.bind("myBean2", b2);
        registry.bind("myBean3", b3);
    }

}
