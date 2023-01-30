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

import java.time.Duration;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

public class SedaDefaultDiscardWhenFullTest extends ContextTestSupport {

    @Test
    public void testDiscardWhenFull() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceivedInAnyOrder("Hello World", "Bye World", "Camel World");

        template.sendBody("seda:foo", "Hello World");
        template.sendBody("seda:foo", "Bye World");

        // this message will be discarded
        template.sendBody("seda:foo", "Hi World");

        // start route
        context.getRouteController().startRoute("foo");

        // wait until at least 1 message has been consumed
        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> mock.getReceivedCounter() >= 1);

        // and now there is room for me
        template.sendBody("seda:foo", "Camel World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                SedaComponent seda = context.getComponent("seda", SedaComponent.class);
                seda.setDefaultDiscardWhenFull(true);
                seda.setQueueSize(2);

                from("seda:foo").routeId("foo").noAutoStartup()
                        .to("mock:result");
            }
        };
    }
}
