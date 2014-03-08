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
package org.apache.camel.component.netty4;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

/**
 * @version 
 */
public class NettyInOutFromSedaTest extends BaseNettyTest {

    @Test
    public void testInOutIssue() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceivedInAnyOrder("Bye A", "Bye B", "Bye C");

        template.sendBody("seda:start", "A");
        template.sendBody("seda:start", "B");
        template.sendBody("seda:start", "C");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:start")
                    .log("before ${body}")
                    .to("netty4:tcp://localhost:{{port}}?textline=true&sync=true")
                    .log("after ${body}")
                    .to("mock:result");

                from("netty4:tcp://localhost:{{port}}?textline=true&sync=true")
                    .transform(body().prepend("Bye "));
            }
        };
    }
}
