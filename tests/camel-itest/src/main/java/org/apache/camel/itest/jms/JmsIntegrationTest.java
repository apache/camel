/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.itest.jms;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentClientAcknowledge;

import javax.jms.ConnectionFactory;
import javax.jms.Message;
import javax.jms.MessageListener;

import junit.framework.TestCase;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.JmsEndpoint;
import org.apache.camel.component.jms.JmsExchange;
import org.apache.camel.component.jms.JmsMessage;
import org.apache.camel.component.pojo.PojoComponent;
import org.apache.camel.impl.DefaultCamelContext;
import edu.emory.mathcs.backport.java.util.concurrent.CountDownLatch;
import edu.emory.mathcs.backport.java.util.concurrent.TimeUnit;

/**
 * @version $Revision:520964 $
 */
public class JmsIntegrationTest extends TestCase {
	
    protected CamelContext container = new DefaultCamelContext();

    public void testDummy() {
    }
    
    /**
     * Commented out since this fails due to us not converting the JmsExchange to a PojoExchange
     * 
     * @throws Exception
     */
	public void xtestOneWayInJmsOutPojo() throws Exception {
		
		final CountDownLatch receivedCountDown = new CountDownLatch(1);
		
        // Configure the components
        ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://localhost?broker.persistent=false");
        container.addComponent("activemq", jmsComponentClientAcknowledge(connectionFactory));
        PojoComponent component = new PojoComponent();
        component.addService("listener", new MessageListener(){
			public void onMessage(Message msg) {
				System.out.println("Received: "+msg);
				receivedCountDown.countDown();				
			}
		});
        container.addComponent("default", component);

        // lets add a jms -> pojo route
        container.addRoutes(new RouteBuilder() {
            public void configure() {
                from("jms:test").to("pojo:listener");
            }
        });
        
        container.start();
        
        // Send a message to the JMS endpoint
        JmsEndpoint endpoint = (JmsEndpoint) container.getEndpoint("jms:test");        
        Producer<JmsExchange> producer = endpoint.createProducer();
        JmsExchange exchange = producer.createExchange();
        JmsMessage in = exchange.getIn();
        in.setBody("Hello");
        in.setHeader("cheese", 123);
        producer.process(exchange);
        
        // The Activated endpoint should send it to the pojo due to the configured route.
        assertTrue("The message ware received by the Pojo", receivedCountDown.await(5, TimeUnit.SECONDS));
        

	}

    @Override
    protected void tearDown() throws Exception {
        container.stop();

        super.tearDown();
    }
}
