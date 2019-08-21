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

public class LoopCopyTest extends ContextTestSupport {

    @Test
    public void testLoopCopy() throws Exception {
        getMockEndpoint("mock:loop").expectedBodiesReceived("AB", "AB", "AB");
        getMockEndpoint("mock:result").expectedBodiesReceived("AB");

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
                    // instruct loop to use copy mode, which mean it will use a
                    // copy of the input exchange
                    // for each loop iteration, instead of keep using the same
                    // exchange all over
                    .loop(3).copy().transform(body().append("B")).to("mock:loop").end().to("mock:result");
                // END SNIPPET: e1
            }
        };
    }
}
