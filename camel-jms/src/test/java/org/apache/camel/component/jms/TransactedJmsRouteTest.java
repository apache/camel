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
package org.apache.camel.component.jms;

import static org.apache.camel.component.mock.MockEndpoint.assertIsSatisfied;

import javax.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
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

/**
 * @version $Revision: 529902 $
 */
public class TransactedJmsRouteTest extends ContextTestSupport {
	
	private static final transient Log log = LogFactory.getLog(TransactedJmsRouteTest.class);
	private MockEndpoint mockEndpointA;
	private MockEndpoint mockEndpointB;
	private ClassPathXmlApplicationContext spring;

	@Override
	protected RouteBuilder createRouteBuilder() {
		return new SpringRouteBuilder<Exchange>() {
			public void configure() {
				
		        Policy requried = new SpringTransactionPolicy(bean(TransactionTemplate.class, "PROPAGATION_REQUIRED"));
		        Policy notsupported = new SpringTransactionPolicy(bean(TransactionTemplate.class, "PROPAGATION_NOT_SUPPORTED"));
		        Policy requirenew = new SpringTransactionPolicy(bean(TransactionTemplate.class, "PROPAGATION_REQUIRES_NEW"));

		        DelegateProcessor rollback = new DelegateProcessor() {
		        	@Override
		        	public void process(Object exchange) {
		        		processNext(exchange);
		        		throw new RuntimeException("rollback");
		        	}
		        };
		        				
		        // Used to verify that transacted sends will succeed.
				from("activemq:queue:mock.a").trace().to("mock:a");      // Used to validate messages are sent to the target.
		        
				// Receive from a and send to target in 1 tx.
		        transactionPolicy("PROPAGATION_REQUIRED");
				from("activemq:queue:a").trace().to("activemq:queue:mock.a");
				
				// Cause an error after processing the send.  The send to activemq:queue:mock.a should rollback 
				// since it is participating in the inbound transaction, but mock:b does not participate so we should see the message get
				// there.  Also, expect multiple inbound retries as the message is rolled back.
				from("activemq:queue:b").inheritErrorHandler(false).trace().intercept(rollback).to("activemq:queue:mock.a", "mock:b"); 

			}
		};
	}
	
    protected CamelContext createCamelContext() throws Exception {
        spring = new ClassPathXmlApplicationContext("org/apache/camel/component/jms/spring.xml");
        SpringCamelContext ctx =  SpringCamelContext.springCamelContext(spring);
        PlatformTransactionManager transactionManager = (PlatformTransactionManager) spring.getBean("jmsTransactionManager");
        ConnectionFactory connectionFactory = (ConnectionFactory) spring.getBean("jmsConnectionFactory");
        JmsComponent component = JmsComponent.jmsComponentTransacted(connectionFactory, transactionManager);
        component.getConfiguration().setConcurrentConsumers(1);
		ctx.addComponent("activemq", component);
        return ctx;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mockEndpointA = (MockEndpoint) resolveMandatoryEndpoint("mock:a");
        mockEndpointB = (MockEndpoint) resolveMandatoryEndpoint("mock:b");
    }

	public void testReuqiredSend() throws Exception {
		String expected = getName()+": "+System.currentTimeMillis();
        mockEndpointA.expectedBodiesReceived(expected);
        send("activemq:queue:a", expected);
        assertIsSatisfied(mockEndpointA);
	}

	public void testRequiredSendAndRollback() throws Exception {
		String expected = getName()+": "+System.currentTimeMillis();
        mockEndpointA.expectedMessageCount(0);
        mockEndpointB.expectedMinimumMessageCount(5); // May be more since spring seems to go into tight loop redelivering.
        send("activemq:queue:b", expected);
        assertIsSatisfied(mockEndpointA,mockEndpointB);
        int t = mockEndpointB.getReceivedCounter();
        System.out.println("Actual Deliveries: "+t);
	}

	/** 
	 * Validates that the send was done in a new transaction.  Message should be consumed from A,
	 * But
	 * 
	 * @throws Exception
	 */
	public void xtestSendRequireNewAndRollack() throws Exception {
		String expected = getName()+": "+System.currentTimeMillis();
        mockEndpointA.expectedMessageCount(0);

        send("activemq:queue:a", expected);

        assertIsSatisfied(mockEndpointA);
	}

}
