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
package org.apache.camel.component.jms.issues;

import javax.jms.ConnectionFactory;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import static org.apache.camel.component.jms.JmsComponent.jmsComponentClientAcknowledge;

/**
 * Unit test for request-reply with jms where processing the input could cause: OK, FAULT or Exception
 */
public class BruceHandlingBeanExceptionTest extends ContextTestSupport {

    public void testSendOK() throws Exception {
        Object out = template.requestBody("activemq:queue:ok", "Hello World");
        assertEquals("Bye World", out);
    }

    public void testSendFailure() throws Exception {
        Object out = template.requestBody("activemq:queue:fault", "Hello World");
        assertEquals("This is a fault message", out);
    }

    public void xxxtestSendError() throws Exception {
        // TODO: See CAMEL-585
        Object out = template.requestBody("activemq:queue:error", "Hello World");
        assertEquals("Damm", out);
    }

    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ConnectionFactory connectionFactory = new ActiveMQConnectionFactory(
            "vm://localhost?broker.persistent=false");
        camelContext.addComponent("activemq", jmsComponentClientAcknowledge(connectionFactory));

        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("activemq:queue:ok").transform(constant("Bye World"));

                from("activemq:queue:fault").setFaultBody(constant("This is a fault message"));

                from("activemq:queue:error").bean(MyExceptionBean.class);
            }
        };
    }

    public static class MyExceptionBean {
        public String doSomething(String input) throws Exception {
            throw new IllegalArgumentException("Forced exception by unit test");
        }
    }
}
