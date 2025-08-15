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
package org.apache.camel.component.netty;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

public class NettyUDPProducerOptionsTest extends BaseNettyTest {

    @Test
    public void testUDPInOnlyMulticastWithNettyConsumer() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBody("direct:sendMessage", "Hello World");

        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {

        return new RouteBuilder() {
            @Override
            public void configure() {
                                from("netty:udp://230.0.0.5:{{port}}?sync=false&networkInterface=eth8")
                                        .log("Received message: ${body}")
                                        .to("mock:result");

                from("direct:sendMessage")
                        .log("Sending message: ${body}")
                        .to("netty:udp://230.0.0.5:{{port}}?sync=false&networkInterface=eth8&option.IP_MULTICAST_TTL=188");
            }
        };
    }

}
