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

import javax.jms.ConnectionFactory;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.junit4.TestSupport;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class LoanBrokerQueueTest extends TestSupport {
    protected CamelContext camelContext;
    protected JmsBroker broker;
    protected ProducerTemplate template;
 
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

        camelContext.addRoutes(new LoanBrokerRoute());
       
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
        String out = template.requestBodyAndHeader("jms:queue:loan", null, Constants.PROPERTY_SSN, "Client-A", String.class);
        
        log.info("Result: {}", out);
        assertNotNull(out);
        assertTrue(out.startsWith("The best rate is [ssn:Client-A bank:bank"));
    }

}
