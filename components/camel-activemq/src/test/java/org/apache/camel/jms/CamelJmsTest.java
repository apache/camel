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
package org.apache.camel.jms;

import javax.jms.Connection;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Session;

import org.apache.camel.CamelContext;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spring.SpringTestSupport;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @version $Revision: $
 */
public class CamelJmsTest extends SpringTestSupport {

    protected String expectedBody = "<hello>world!</hello>";

	public void testCamelJms() throws Exception {
        CamelConnectionFactory factory = getMandatoryBean(CamelConnectionFactory.class, "connectionFactory");

        CamelContext context = factory.getCamelContext();
		assertNotNull("Should have a CamelContext!", context);
        
		MockEndpoint result = context.getEndpoint("mock:result", MockEndpoint.class);
		assertNotNull("Should have a MockEndpoint!", result);
		
		result.expectedBodiesReceived(expectedBody);
		result.message(0).header("foo").isEqualTo("bar");
		
        // lets create a message
        Connection connection = factory.createConnection();
        connection.start();
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        CamelQueue destination = new CamelQueue("mock:result");
        destination.setCamelContext(context);
		MessageProducer producer = session.createProducer(destination);
		
		// now lets send a message
		ObjectMessage message = session.createObjectMessage(expectedBody);
		message.setStringProperty("foo", "bar");
		producer.send(message);
		
		result.assertIsSatisfied();

		log.info("Received message: "+ result.getReceivedExchanges());
	}

    protected int getExpectedRouteCount() {
        return 0;
    }

    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/jms/spring.xml");
    }
}
