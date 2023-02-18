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
package org.apache.camel.builder;

import java.util.List;

import org.apache.camel.Channel;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.impl.engine.DefaultRoute;
import org.apache.camel.model.RedeliveryPolicyDefinition;
import org.apache.camel.processor.SendProcessor;
import org.apache.camel.processor.errorhandler.DeadLetterChannel;
import org.apache.camel.processor.errorhandler.RedeliveryPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ContextErrorHandlerTest extends ContextTestSupport {

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        setUseRouteBuilder(false);
        super.setUp();
        RedeliveryPolicyDefinition redeliveryPolicy = new RedeliveryPolicyDefinition();
        redeliveryPolicy.maximumRedeliveries(1);
        redeliveryPolicy.setUseExponentialBackOff("true");
        DeadLetterChannelBuilder deadLetterChannelBuilder = new DeadLetterChannelBuilder("mock:error");
        deadLetterChannelBuilder.setRedeliveryPolicy(redeliveryPolicy);
        context.getCamelContextExtension().setErrorHandlerFactory(deadLetterChannelBuilder);
    }

    @Override
    protected void startCamelContext() throws Exception {
        // do nothing here
    }

    @Override
    protected void stopCamelContext() throws Exception {
        // do nothing here
    }

    protected List<Route> getRouteListWithCurrentContext(RouteBuilder builder) throws Exception {
        context.addRoutes(builder);
        context.start();
        List<Route> answer = context.getRoutes();
        context.stop();
        return answer;
    }

    @Test
    public void testOverloadingTheDefaultErrorHandler() throws Exception {

        RouteBuilder builder = new RouteBuilder() {
            public void configure() {
                errorHandler(deadLetterChannel("log:FOO.BAR"));
                from("seda:a").to("seda:b");
            }
        };

        List<Route> list = getRouteListWithCurrentContext(builder);
        assertEquals(1, list.size(), "Number routes created" + list);
        for (Route route : list) {
            Endpoint key = route.getEndpoint();
            assertEquals("seda://a", key.getEndpointUri(), "From endpoint");

            DefaultRoute consumerRoute = assertIsInstanceOf(DefaultRoute.class, route);
            Processor processor = consumerRoute.getProcessor();

            Channel channel = unwrapChannel(processor);
            assertIsInstanceOf(DeadLetterChannel.class, channel.getErrorHandler());
            SendProcessor sendProcessor = assertIsInstanceOf(SendProcessor.class, channel.getNextProcessor());
            log.debug("Found sendProcessor: {}", sendProcessor);
        }
    }

    @Test
    public void testGetTheDefaultErrorHandlerFromContext() throws Exception {

        RouteBuilder builder = new RouteBuilder() {
            public void configure() {
                from("seda:a").to("seda:b");
                from("direct:c").to("direct:d");
            }
        };

        List<Route> list = getRouteListWithCurrentContext(builder);
        assertEquals(2, list.size(), "Number routes created" + list);
        for (Route route : list) {

            DefaultRoute consumerRoute = assertIsInstanceOf(DefaultRoute.class, route);
            Processor processor = consumerRoute.getProcessor();

            Channel channel = unwrapChannel(processor);
            DeadLetterChannel deadLetterChannel = assertIsInstanceOf(DeadLetterChannel.class, channel.getErrorHandler());

            RedeliveryPolicy redeliveryPolicy = deadLetterChannel.getRedeliveryPolicy();

            assertEquals(1, redeliveryPolicy.getMaximumRedeliveries(), "getMaximumRedeliveries()");
            assertTrue(redeliveryPolicy.isUseExponentialBackOff(), "isUseExponentialBackOff()");
        }
    }

}
