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
package org.apache.camel.processor;

import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.EventDrivenConsumerRoute;
import org.apache.camel.processor.interceptor.DefaultChannel;
import org.apache.camel.processor.interceptor.StreamCaching;

/**
 * @version 
 */
public class ResequencerTest extends ContextTestSupport {
    protected Endpoint startEndpoint;
    protected MockEndpoint resultEndpoint;

    public void testSendMessagesInWrongOrderButReceiveThemInCorrectOrder() throws Exception {
        resultEndpoint.expectedBodiesReceived("Guillaume", "Hiram", "James", "Rob");
        sendBodies("direct:start", "Rob", "Hiram", "Guillaume", "James");
        resultEndpoint.assertIsSatisfied();
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        resultEndpoint = getMockEndpoint("mock:result");
    }

    @Override 
    protected void tearDown() throws Exception {
        super.tearDown();
    }
    
    protected boolean useJmx() {
        // use jmx only when running the following test(s)
        return getName().equals("testBatchResequencerTypeWithJmx");
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // START SNIPPET: example
                from("direct:start")
                    .resequence().body().timeout(50)
                    .to("mock:result");
                // END SNIPPET: example
            }
        };
    }

    public void testBatchResequencerTypeWithJmx() throws Exception {
        testBatchResequencerTypeWithoutJmx();
    }

    public void testBatchResequencerTypeWithoutJmx() throws Exception {
        List<Route> list = getRouteList(createRouteBuilder());
        assertEquals("Number of routes created: " + list, 1, list.size());

        Route route = list.get(0);
        EventDrivenConsumerRoute consumerRoute = assertIsInstanceOf(EventDrivenConsumerRoute.class, route);

        DefaultChannel channel = assertIsInstanceOf(DefaultChannel.class, unwrapChannel(consumerRoute.getProcessor()));

        assertIsInstanceOf(DefaultErrorHandler.class, channel.getErrorHandler());
        assertFalse("Should not have stream caching", channel.hasInterceptorStrategy(StreamCaching.class));

        assertIsInstanceOf(Resequencer.class, channel.getNextProcessor());
    }

}
