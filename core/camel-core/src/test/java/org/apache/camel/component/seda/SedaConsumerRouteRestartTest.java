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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

class SedaConsumerRouteRestartTest extends ContextTestSupport {

    @Test
    void testConsumerFirstRestartAfterQueueRelease() throws Exception {
        // stopping the consumer route first and the producer route last releases the shared queue
        // reference; restarting the consumer route first must not leave its pollers on the orphaned
        // queue while a later restarted producer registers a fresh one
        context.getRouteController().stopRoute("consumer");
        context.getRouteController().stopRoute("producer");

        context.getRouteController().startRoute("consumer");
        context.getRouteController().startRoute("producer");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("after-restart");

        template.sendBody("direct:start", "after-restart");

        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("producer").to("seda:bar");
                from("seda:bar").routeId("consumer").to("mock:result");
            }
        };
    }
}
