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
package org.apache.camel.component.sjms;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.sjms.support.JmsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * Testing with async start listener
 */
public class AsyncStartStopListenerTest extends JmsTestSupport {

    @Test
    public void testAsyncStartConsumer() throws Exception {
        sendBodyAndAssert("sjms:queue:foo.start.AsyncStartStopListenerTest");
    }

    @Test
    public void testAsyncStartStopConsumer() throws Exception {
        sendBodyAndAssert("sjms:queue:foo.startstop.AsyncStartStopListenerTest");
    }

    @Test
    public void testAsyncStopConsumer() throws Exception {
        sendBodyAndAssert("sjms:queue:foo.stop.AsyncStartStopListenerTest");
    }

    @Test
    public void testAsyncStopProducer() throws Exception {
        sendBodyAndAssert("sjms:queue:foo.AsyncStartStopListenerTest?asyncStopListener=true");
    }

    @Test
    public void testAsyncStartProducer() throws Exception {
        sendBodyAndAssert("sjms:queue:foo.AsyncStartStopListenerTest?asyncStartListener=true");
    }

    @Test
    public void testAsyncStartStopProducer() throws Exception {
        sendBodyAndAssert("sjms:queue:foo.AsyncStartStopListenerTest?asyncStopListener=true&asyncStartListener=true");
    }

    private void sendBodyAndAssert(final String uri) throws InterruptedException {
        String body1 = "Hello World";
        String body2 = "G'day World";
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedBodiesReceived(body1, body2);
        template.sendBody(uri, body1);
        template.sendBody(uri, body2);
        result.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("sjms:queue:foo.startstop.AsyncStartStopListenerTest?asyncStartListener=true&asyncStopListener=true")
                        .to("mock:result");
                from("sjms:queue:foo.start.AsyncStartStopListenerTest?asyncStartListener=true").to("mock:result");
                from("sjms:queue:foo.stop.AsyncStartStopListenerTest?asyncStopListener=true").to("mock:result");
                from("sjms:queue:foo.AsyncStartStopListenerTest").to("mock:result");
            }
        };
    }
}
