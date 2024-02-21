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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.TransientCamelContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JmsInOnlyDisableTimeToLiveTest extends AbstractJMSTest {

    private static final Logger LOG = LoggerFactory.getLogger(JmsInOnlyDisableTimeToLiveTest.class);

    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new TransientCamelContextExtension();
    protected CamelContext context;
    protected ProducerTemplate template;
    protected ConsumerTemplate consumer;
    private final String urlTimeout = "activemq:JmsInOnlyDisableTimeToLiveTest.in?timeToLive=2000";
    private final String urlTimeToLiveDisabled
            = "activemq:JmsInOnlyDisableTimeToLiveTest.in?timeToLive=2000&disableTimeToLive=true";
    private CountDownLatch messageWasExpiredCountDownLatch = new CountDownLatch(2);

    @Test
    public void testInOnlyExpired() throws Exception {
        MyCoolBean cool = new MyCoolBean(consumer, template, "JmsInOnlyDisableTimeToLiveTest");

        getMockEndpoint("mock:result").expectedBodiesReceived("World 1");

        // setup a message that will timeout to prove the ttl is getting set
        // and that the disableTimeToLive is defaulting to false
        template.sendBody("direct:timeout", "World 1");

        MockEndpoint.assertIsSatisfied(context);

        // wait after the msg has expired
        messageWasExpiredCountDownLatch.await(2000, TimeUnit.MILLISECONDS);

        MockEndpoint.resetMocks(context);
        getMockEndpoint("mock:end").expectedMessageCount(0);

        cool.someBusinessLogic();

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testInOnlyDisabledTimeToLive() throws Exception {
        MyCoolBean cool = new MyCoolBean(consumer, template, "JmsInOnlyDisableTimeToLiveTest");

        getMockEndpoint("mock:result").expectedBodiesReceived("World 2");

        // send a message that sets the requestTimeout to 2 secs with a
        //      disableTimeToLive set to true, this should timeout
        //      but leave the message on the queue to be processed
        //      by the CoolBean
        template.sendBody("direct:disable", "World 2");

        MockEndpoint.assertIsSatisfied(context);

        // wait after the msg has expired
        messageWasExpiredCountDownLatch.await(2000, TimeUnit.MILLISECONDS);

        MockEndpoint.resetMocks(context);
        getMockEndpoint("mock:end").expectedBodiesReceived("Hello World 2");

        cool.someBusinessLogic();

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

                from("activemq:JmsInOnlyDisableTimeToLiveTest.out")
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
