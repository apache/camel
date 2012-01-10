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
package org.apache.camel.loanbroker.queue.version;

import java.util.List;
import javax.jms.ConnectionFactory;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.junit4.TestSupport;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class LoanBrokerTest extends TestSupport {
    private CamelContext camelContext;
    private JmsBroker broker;
    private ProducerTemplate template;
 
    @Before
    public void startServices() throws Exception {
        deleteDirectory("activemq-data");

        camelContext = new DefaultCamelContext();
        broker = new JmsBroker("vm://localhost");
        broker.start();
        // Set up the ActiveMQ JMS Components
        ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://localhost");

        // Note we can explicitly name the component
        camelContext.addComponent("jms", JmsComponent.jmsComponentAutoAcknowledge(connectionFactory));

        camelContext.addRoutes(new LoanBroker());
        
        camelContext.addRoutes(new RouteBuilder() {
            // using the mock endpoint to check the result
            public void configure() throws Exception {
                from("jms:queue:loanReplyQueue").to("mock:endpoint");
            }
        });
       
        template = camelContext.createProducerTemplate();
        camelContext.start();
    }
    
    @After
    public void stopServices() throws Exception {
        if (camelContext != null) {
            camelContext.stop();
        }
        
        Thread.sleep(1000);
        if (broker != null) {
            broker.stop();
        }
    }
    
    @Test
    public void testClientInvocation() throws Exception {
        MockEndpoint endpoint = (MockEndpoint) camelContext.getEndpoint("mock:endpoint");
        endpoint.expectedMessageCount(2);
        // send out the request message
        for (int i = 0; i < 2; i++) {
            template.sendBodyAndHeader("jms:queue:loanRequestQueue",
                                       "Quote for the lowerst rate of loaning bank",
                                       Constants.PROPERTY_SSN, "Client-A" + i);
            Thread.sleep(100);
        }
        endpoint.assertIsSatisfied();

        // check the response from the mock endpoint
        List<Exchange> exchanges = endpoint.getExchanges();
        int index = 0;
        for (Exchange exchange : exchanges) {
            String ssn = "Client-A" + index;
            String result = exchange.getIn().getBody(String.class);
            assertNotNull("The result should not be null", result);
            assertTrue("The result is wrong", result.startsWith("Loan quotion for Client " + ssn));
            index++;
        }
        
        // send the request and get the response from the same queue       
        Exchange exchange = template.send("jms:queue2:parallelLoanRequestQueue", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.setPattern(ExchangePattern.InOut);
                exchange.getIn().setBody("Quote for the lowerst rate of loaning bank");
                exchange.getIn().setHeader(Constants.PROPERTY_SSN, "Client-B");
            }
        });
        
        String bank = exchange.getOut().getHeader(Constants.PROPERTY_BANK, String.class);
        Double rate = exchange.getOut().getHeader(Constants.PROPERTY_RATE, Double.class);
        String ssn = exchange.getOut().getHeader(Constants.PROPERTY_SSN, String.class);
        
        assertNotNull("The ssn should not be null.", ssn);
        assertEquals("Get a wrong ssn", "Client-B",  ssn);
        assertNotNull("The bank should not be null", bank);
        assertNotNull("The rate should not be null", rate);
    }

}
