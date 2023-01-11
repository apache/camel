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
package org.apache.camel.component.paho;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

public class PahoToDTest extends PahoTestSupport {

    @Override
    protected boolean useJmx() {
        return false;
    }

    @Test
    public void testToD() throws Exception {
        MockEndpoint bar = getMockEndpoint("mock:bar");
        bar.expectedBodiesReceived("Hello bar", null); // issue with Artemis
        MockEndpoint beer = getMockEndpoint("mock:beer");
        beer.expectedBodiesReceived("Hello beer");

        template.sendBodyAndHeader("direct:start", "Hello bar", "where", "bar");
        template.sendBodyAndHeader("direct:start", "Hello beer", "where", "beer");

        bar.assertIsSatisfied();
        beer.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                PahoComponent paho = context.getComponent("paho", PahoComponent.class);
                paho.getConfiguration().setBrokerUrl("tcp://localhost:" + service.brokerPort());

                // route message dynamic using toD
                from("direct:start").toD("paho:${header.where}");

                from("paho:bar").to("mock:bar");
                from("paho:beer").to("mock:beer");
            }
        };
    }

}
