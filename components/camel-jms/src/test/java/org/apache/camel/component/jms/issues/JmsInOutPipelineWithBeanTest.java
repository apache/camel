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

import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.AbstractJMSTest;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit test from an user request on the forum.
 */
public class JmsInOutPipelineWithBeanTest extends AbstractJMSTest {

    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();
    protected CamelContext context;
    protected ProducerTemplate template;
    protected ConsumerTemplate consumer;
    @BindToRegistry("dummyBean")
    private final MyDummyBean bean = new MyDummyBean();

    @Test
    public void testA() {
        Object response = template.requestBody("activemq:JmsInOutPipelineWithBeanTest.A", "Hello World");
        assertEquals("Hello World,From Bean,From A,From B", response, "Reply");
    }

    @Test
    public void testB() {
        Object response = template.requestBody("activemq:JmsInOutPipelineWithBeanTest.B", "Hello World");
        assertEquals("Hello World,From A,From Bean,From B", response, "Reply");
    }

    @Test
    public void testC() {
        Object response = template.requestBody("activemq:JmsInOutPipelineWithBeanTest.C", "Hello World");
        assertEquals("Hello World,From A,From B,From Bean", response, "Reply");
    }

    @Override
    protected String getComponentName() {
        return "activemq";
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("activemq:JmsInOutPipelineWithBeanTest.A").to("bean:dummyBean")
                        .to("activemq:JmsInOutPipelineWithBeanTest.dest.a").to("activemq:JmsInOutPipelineWithBeanTest.dest.b");
                from("activemq:JmsInOutPipelineWithBeanTest.B").to("activemq:JmsInOutPipelineWithBeanTest.dest.a")
                        .to("bean:dummyBean").to("activemq:JmsInOutPipelineWithBeanTest.dest.b");
                from("activemq:JmsInOutPipelineWithBeanTest.C").to("activemq:JmsInOutPipelineWithBeanTest.dest.a")
                        .to("activemq:JmsInOutPipelineWithBeanTest.dest.b").to("bean:dummyBean");

                from("activemq:JmsInOutPipelineWithBeanTest.dest.a").process(exchange -> {
                    String body = exchange.getIn().getBody(String.class);
                    exchange.getMessage().setBody(body + ",From A");
                });

                from("activemq:JmsInOutPipelineWithBeanTest.dest.b").process(exchange -> {
                    String body = exchange.getIn().getBody(String.class);
                    exchange.getMessage().setBody(body + ",From B");
                });
            }
        };
    }

    @Override
    public CamelContextExtension getCamelContextExtension() {
        return camelContextExtension;
    }

    @BeforeEach
    void setUpRequirements() {
        context = camelContextExtension.getContext();
        template = camelContextExtension.getProducerTemplate();
        consumer = camelContextExtension.getConsumerTemplate();
    }

    public static class MyDummyBean {
        public void doSomething(Exchange exchange) {
            String body = exchange.getIn().getBody(String.class);
            exchange.getMessage().setBody(body + ",From Bean");
        }
    }

}
