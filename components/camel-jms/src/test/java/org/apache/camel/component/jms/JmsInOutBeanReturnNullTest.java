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

import java.io.Serializable;
import javax.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

/**
 *
 */
public class JmsInOutBeanReturnNullTest extends CamelTestSupport {

    @Test
    public void testReturnBean() throws Exception {
        MyBean out = template.requestBody("activemq:queue:foo", "Camel", MyBean.class);
        assertNotNull(out);
        assertEquals("Camel", out.getName());
    }

    @Test
    public void testReturnNull() throws Exception {
        Object out = template.requestBody("activemq:queue:foo", "foo");
        assertNull(out);
    }

    @Test
    public void testReturnNullMyBean() throws Exception {
        MyBean out = template.requestBody("activemq:queue:foo", "foo", MyBean.class);
        assertNull(out);
    }

    @Test
    public void testReturnNullExchange() throws Exception {
        Exchange reply = template.request("activemq:queue:foo", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("foo");
            }
        });
        assertNotNull(reply);
        assertTrue(reply.hasOut());
        Message out = reply.getOut();
        assertNotNull(out);
        Object body = out.getBody();
        assertNull("Should be a null body", body);
    }

    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
        camelContext.addComponent("activemq", jmsComponentAutoAcknowledge(connectionFactory));
        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("activemq:queue:foo")
                    .bean(JmsInOutBeanReturnNullTest.class, "doSomething");
            }
        };
    }

    public MyBean doSomething(String body) {
        if ("foo".equals(body)) {
            return null;
        } else {
            return new MyBean(body);
        }
    }

    public static final class MyBean implements Serializable {

        private static final long serialVersionUID = 1L;
        public String name;

        public MyBean(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }
}