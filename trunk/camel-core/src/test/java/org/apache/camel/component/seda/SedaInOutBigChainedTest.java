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
package org.apache.camel.component.seda;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;

/**
 * @version 
 */
public class SedaInOutBigChainedTest extends ContextTestSupport {

    public void testInOutBigSedaChained() throws Exception {
        getMockEndpoint("mock:a").expectedBodiesReceived("start");
        getMockEndpoint("mock:b").expectedBodiesReceived("start-a");
        getMockEndpoint("mock:c").expectedBodiesReceived("start-a-b");
        getMockEndpoint("mock:d").expectedBodiesReceived("start-a-b-c");
        getMockEndpoint("mock:e").expectedBodiesReceived("start-a-b-c-d");
        getMockEndpoint("mock:f").expectedBodiesReceived("start-a-b-c-d-e");
        getMockEndpoint("mock:g").expectedBodiesReceived("start-a-b-c-d-e-f");
        getMockEndpoint("mock:h").expectedBodiesReceived("start-a-b-c-d-e-f-g");

        String reply = template.requestBody("seda:a", "start", String.class);
        assertEquals("start-a-b-c-d-e-f-g-h", reply);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:a").to("mock:a").transform(simple("${body}-a")).to("seda:b");

                from("seda:b").to("mock:b").transform(simple("${body}-b")).to("seda:c");

                from("seda:c").to("mock:c").transform(simple("${body}-c")).to("seda:d");

                from("seda:d").to("mock:d").transform(simple("${body}-d")).to("seda:e");

                from("seda:e").to("mock:e").transform(simple("${body}-e")).to("seda:f");

                from("seda:f").to("mock:f").transform(simple("${body}-f")).to("seda:g");

                from("seda:g").to("mock:g").transform(simple("${body}-g")).to("seda:h");

                from("seda:h").to("mock:h").transform(simple("${body}-h"));
            }
        };
    }
}