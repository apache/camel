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
import org.junit.Test;

public class LoopAsyncNoCopyTest extends ContextTestSupport {

    @Test
    public void testLoopNoCopy() throws Exception {
        getMockEndpoint("mock:loop").expectedBodiesReceived("AB", "ABB", "ABBB");
        getMockEndpoint("mock:result").expectedBodiesReceived("ABBB");

        template.sendBody("direct:start", "A");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // START SNIPPET: e1
                from("direct:start")
                    // by default loop will keep using the same exchange so on
                    // the 2nd and 3rd iteration its
                    // the same exchange that was previous used that are being
                    // looped all over
                    .loop(3).threads(1).transform(body().append("B")).end().to("mock:loop").end().to("mock:result");
                // END SNIPPET: e1
            }
        };
    }
}
