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
package org.apache.camel.component.jms;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.fail;

public class JmsInOutDisableTimeToLiveTest extends AbstractJMSTest {

    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();
    protected CamelContext context;
    protected ProducerTemplate template;
    protected ConsumerTemplate consumer;
    private final String urlTimeout = "activemq:JmsInOutDisableTimeToLiveTest.in?requestTimeout=2000";
    private final String urlTimeToLiveDisabled
            = "activemq:JmsInOutDisableTimeToLiveTest.in?requestTimeout=2000&disableTimeToLive=true";

    @Test
    public void testInOutExpired() throws Exception {
        MyCoolBean cool = new MyCoolBean(consumer, template, "JmsInOutDisableTimeToLiveTest");

        getMockEndpoint("mock:result").expectedMessageCount(0);
        getMockEndpoint("mock:end").expectedMessageCount(0);

        // setup a message that will timeout to prove the ttl is getting set
        //      and that the disableTimeToLive is defaulting to false
        try {
            template.requestBody("direct:timeout", "World 1");
            fail("Should not get here, timeout expected");
        } catch (CamelExecutionException e) {
            cool.someBusinessLogic();
        }

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testInOutDisableTimeToLive() throws Exception {
        MyCoolBean cool = new MyCoolBean(consumer, template, "JmsInOutDisableTimeToLiveTest");

        getMockEndpoint("mock:result").expectedMessageCount(0);
        getMockEndpoint("mock:end").expectedBodiesReceived("Hello World 2");

        // send a message that sets the requestTimeout to 2 secs with a
        //      disableTimeToLive set to true, this should timeout
        //      but leave the message on the queue to be processed
        //      by the CoolBean
        try {
            template.requestBody("direct:disable", "World 2");
            fail("Should not get here, timeout expected");
        } catch (CamelExecutionException e) {
            cool.someBusinessLogic();
        }

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected String getComponentName() {
        return "activemq";
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:timeout")
                        .to(urlTimeout)
                        .to("mock:result");

                from("direct:disable")
                        .to(urlTimeToLiveDisabled)
                        .to("mock:result");

                from("activemq:JmsInOutDisableTimeToLiveTest.out")
                        .to("mock:end");
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
}
