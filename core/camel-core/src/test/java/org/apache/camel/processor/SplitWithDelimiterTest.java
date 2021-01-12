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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

public class SplitWithDelimiterTest extends ContextTestSupport {

    @Test
    public void testSplitWithDelimiterDisabled() throws Exception {
        String body = "some , # false text";
        MockEndpoint m = getMockEndpoint("mock:result-0");
        m.expectedPropertyReceived("CamelSplitSize", 1);
        m.expectedBodiesReceived(body);
        template.sendBody("direct:start-0", body);
        m.assertIsSatisfied();
    }

    @Test
    public void testSplitWithDefaultDelimiter() throws Exception {
        String body = "some , # false text";
        MockEndpoint m = getMockEndpoint("mock:result-1");
        m.expectedPropertyReceived("CamelSplitSize", 2);
        template.sendBody("direct:start-1", body);
        m.expectedBodiesReceived("some ", " # false text");
        m.assertIsSatisfied();
    }

    @Test
    public void testSplitWithDelimiter() throws Exception {
        String body = "some , # false # text";
        MockEndpoint m = getMockEndpoint("mock:result-2");
        m.expectedPropertyReceived("CamelSplitSize", 3);
        m.expectedBodiesReceived("some , ", " false ", " text");
        template.sendBody("direct:start-2", body);
        m.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start-0")
                        .split(body(), "false")
                        .to("mock:result-0");

                from("direct:start-1")
                        .split(body())
                        .to("mock:result-1");

                from("direct:start-2")
                        .split(body(), "#")
                        .to("mock:result-2");
            }
        };
    }

}
