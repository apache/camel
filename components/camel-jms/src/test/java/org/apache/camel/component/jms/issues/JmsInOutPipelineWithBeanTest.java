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
package org.apache.camel.component.jms.issues;

import javax.jms.ConnectionFactory;

import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.CamelJmsTestHelper;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit test from an user request on the forum.
 */
public class JmsInOutPipelineWithBeanTest extends CamelTestSupport {

    @BindToRegistry("dummyBean")
    private MyDummyBean bean = new MyDummyBean();

    @Test
    public void testA() throws Exception {
        Object response = template.requestBody("activemq:testA", "Hello World");
        assertEquals("Hello World,From Bean,From A,From B", response, "Reply");
    }

    @Test
    public void testB() throws Exception {
        Object response = template.requestBody("activemq:testB", "Hello World");
        assertEquals("Hello World,From A,From Bean,From B", response, "Reply");
    }

    @Test
    public void testC() throws Exception {
        Object response = template.requestBody("activemq:testC", "Hello World");
        assertEquals("Hello World,From A,From B,From Bean", response, "Reply");
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
        camelContext.addComponent("activemq", jmsComponentAutoAcknowledge(connectionFactory));
        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("activemq:testA").to("bean:dummyBean").to("activemq:a").to("activemq:b");
                from("activemq:testB").to("activemq:a").to("bean:dummyBean").to("activemq:b");
                from("activemq:testC").to("activemq:a").to("activemq:b").to("bean:dummyBean");

                from("activemq:a").process(exchange -> {
                    String body = exchange.getIn().getBody(String.class);
                    exchange.getMessage().setBody(body + ",From A");
                });

                from("activemq:b").process(exchange -> {
                    String body = exchange.getIn().getBody(String.class);
                    exchange.getMessage().setBody(body + ",From B");
                });
            }
        };
    }

    public static class MyDummyBean {
        public void doSomething(Exchange exchange) {
            String body = exchange.getIn().getBody(String.class);
            exchange.getMessage().setBody(body + ",From Bean");
        }
    }

}
