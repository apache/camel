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
package org.apache.camel.builder;

import java.util.List;

import org.apache.camel.Channel;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.impl.EventDrivenConsumerRoute;
import org.apache.camel.processor.DeadLetterChannel;
import org.apache.camel.processor.LoggingErrorHandler;
import org.apache.camel.processor.RedeliveryPolicy;
import org.apache.camel.processor.SendProcessor;

public class ContextErrorHandlerTest extends ContextTestSupport {

    protected void setUp() throws Exception {
        setUseRouteBuilder(false);
        super.setUp();
        RedeliveryPolicy redeliveryPolicy = new RedeliveryPolicy();
        redeliveryPolicy.maximumRedeliveries(1);
        redeliveryPolicy.setUseExponentialBackOff(true);
        DeadLetterChannelBuilder deadLetterChannelBuilder = new DeadLetterChannelBuilder("mock:error");
        deadLetterChannelBuilder.setRedeliveryPolicy(redeliveryPolicy);
        context.setErrorHandlerBuilder(deadLetterChannelBuilder);
    }

    protected void startCamelContext() throws Exception {
        // do nothing here
    }

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

    public void testOverloadingTheDefaultErrorHandler() throws Exception {

        RouteBuilder builder = new RouteBuilder() {
            public void configure() {
                errorHandler(loggingErrorHandler("FOO.BAR"));
                from("seda:a").to("seda:b");
            }
        };

        List<Route> list = getRouteListWithCurrentContext(builder);
        assertEquals("Number routes created" + list, 1, list.size());
        for (Route route : list) {
            Endpoint key = route.getEndpoint();
            assertEquals("From endpoint", "seda://a", key.getEndpointUri());

            EventDrivenConsumerRoute consumerRoute = assertIsInstanceOf(EventDrivenConsumerRoute.class, route);
            Processor processor = consumerRoute.getProcessor();

            Channel channel = unwrapChannel(processor);
            assertIsInstanceOf(LoggingErrorHandler.class, channel.getErrorHandler());
            SendProcessor sendProcessor = assertIsInstanceOf(SendProcessor.class, channel.getNextProcessor());
            log.debug("Found sendProcessor: " + sendProcessor);
        }
    }

    public void testGetTheDefaultErrorHandlerFromContext() throws Exception {

        RouteBuilder builder = new RouteBuilder() {
            public void configure() {
                from("seda:a").to("seda:b");
                from("direct:c").to("direct:d");
            }
        };

        List<Route> list = getRouteListWithCurrentContext(builder);
        assertEquals("Number routes created" + list, 2, list.size());
        for (Route route : list) {

            EventDrivenConsumerRoute consumerRoute = assertIsInstanceOf(EventDrivenConsumerRoute.class, route);
            Processor processor = consumerRoute.getProcessor();

            Channel channel = unwrapChannel(processor);
            DeadLetterChannel deadLetterChannel = assertIsInstanceOf(DeadLetterChannel.class, channel.getErrorHandler());

            RedeliveryPolicy redeliveryPolicy = deadLetterChannel.getRedeliveryPolicy();

            assertEquals("getMaximumRedeliveries()", 1, redeliveryPolicy.getMaximumRedeliveries());
            assertEquals("isUseExponentialBackOff()", true, redeliveryPolicy.isUseExponentialBackOff());
        }
    }

}
