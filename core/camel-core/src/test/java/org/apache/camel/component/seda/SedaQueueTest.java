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
package org.apache.camel.component.seda;

import java.util.concurrent.ArrayBlockingQueue;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class SedaQueueTest extends ContextTestSupport {

    @Test
    public void testQueue() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceivedInAnyOrder("Hello World", "Bye World", "Goodday World", "Bar");

        template.sendBody("seda:foo", "Hello World");
        template.sendBody("seda:foo?size=20", "Bye World");
        template.sendBody("seda:foo?concurrentConsumers=5", "Goodday World");
        template.sendBody("seda:bar", "Bar");
    }

    @Test
    public void testQueueRef() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        template.sendBody("seda:array?queue=#arrayQueue", "Hello World");

        SedaEndpoint sedaEndpoint = resolveMandatoryEndpoint("seda:array?queue=#arrayQueue", SedaEndpoint.class);
        assertTrue(sedaEndpoint.getQueue() instanceof ArrayBlockingQueue);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.getRegistry().bind("arrayQueue", new ArrayBlockingQueue<Exchange>(10));

                from("seda:foo?size=20&concurrentConsumers=2").to("mock:result");

                from("seda:bar").to("mock:result");

                from("seda:array?queue=#arrayQueue").to("mock:result");
            }
        };
    }
}
