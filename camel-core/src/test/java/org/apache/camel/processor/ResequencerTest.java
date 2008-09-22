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
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.EventDrivenConsumerRoute;
import org.apache.camel.management.InstrumentationProcessor;
import org.apache.camel.management.JmxSystemPropertyKeys;

/**
 * @version $Revision$
 */
public class ResequencerTest extends ContextTestSupport {
    protected Endpoint<Exchange> startEndpoint;
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
        System.clearProperty(JmxSystemPropertyKeys.DISABLED);
    }
    
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // START SNIPPET: example
                from("direct:start").resequencer(body()).to("mock:result");
                // END SNIPPET: example
            }
        };
    }

    public void testBatchResequencerTypeWithJmx() throws Exception {
        System.setProperty(JmxSystemPropertyKeys.DISABLED, "true");

        List<Route> list = getRouteList(createRouteBuilder());
        assertEquals("Number of routes created: " + list, 1, list.size());

        Route route = list.get(0);
        assertIsInstanceOf(EventDrivenConsumerRoute.class, route);
    }

    public void testBatchResequencerTypeWithoutJmx() throws Exception {
        List<Route> list = getRouteList(createRouteBuilder());
        assertEquals("Number of routes created: " + list, 1, list.size());

        Route route = list.get(0);
        EventDrivenConsumerRoute consumerRoute =
            assertIsInstanceOf(EventDrivenConsumerRoute.class, route);

        Processor processor = unwrap(consumerRoute.getProcessor());

        DeadLetterChannel deadLetterChannel =
            assertIsInstanceOf(DeadLetterChannel.class, processor);

        Processor outputProcessor = deadLetterChannel.getOutput();
        InstrumentationProcessor interceptor =
                assertIsInstanceOf(InstrumentationProcessor.class, outputProcessor);

        outputProcessor = interceptor.getProcessor();

        assertIsInstanceOf(Resequencer.class, outputProcessor);
    }

}
