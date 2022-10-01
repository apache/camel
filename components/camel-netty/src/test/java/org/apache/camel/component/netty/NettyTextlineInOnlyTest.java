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

public class NettyTextlineInOnlyTest extends BaseNettyTest {

    @Test
    public void testTextlineInOnlyDual() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World", "how are you?");

        template.sendBody("netty:tcp://localhost:{{port}}?textline=true&sync=false", "Hello World\nhow are you?\n");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testTextlineInOnlyAutoAppend() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");

        template.sendBody("netty:tcp://localhost:{{port}}?textline=true&sync=false", "Hello World");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testTextlineInOnly() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");

        template.sendBody("netty:tcp://localhost:{{port}}?textline=true&sync=false", "Hello World\n");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("netty:tcp://localhost:{{port}}?textline=true&sync=false")
                        // body should be a String when using textline codec
                        .validate(body().isInstanceOf(String.class))
                        .to("mock:result");
            }
        };
    }
}
