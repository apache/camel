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

import javax.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.ExchangeTimedOutException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

/**
 * Unit test for testing request timeout with a InOut exchange.
 */
public class JmsRouteTimeoutCheckerIntervalTest extends CamelTestSupport {

    @Test
    public void testTimeout() throws Exception {
        try {
            // send a in-out with a timeout for 1 sec 
            template.requestBody("activemq:queue:slow?requestTimeout=1000", "Hello World");
            fail("Should have timed out with an exception");
        } catch (RuntimeCamelException e) {
            assertTrue("Should have timed out with an exception", e.getCause() instanceof ExchangeTimedOutException);
        }
    }

    @Test
    public void testNoTimeout() throws Exception {
        // START SNIPPET: e1
        // send a in-out with a timeout for 5 sec
        Object out = template.requestBody("activemq:queue:slow?requestTimeout=5000", "Hello World");
        // END SNIPPET: e1
        assertEquals("Bye World", out);
    }

    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
        JmsComponent activmq = jmsComponentAutoAcknowledge(connectionFactory);
        // check 4 times per second
        activmq.setRequestTimeoutCheckerInterval(250);
        camelContext.addComponent("activemq", activmq);

        return camelContext;
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("activemq:queue:slow").delay(3000).transform(constant("Bye World"));
            }
        };
    }
}