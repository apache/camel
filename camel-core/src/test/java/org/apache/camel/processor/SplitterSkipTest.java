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
package org.apache.camel.processor;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;

public class SplitterSkipTest extends ContextTestSupport {

    public void testSplitterSkip() throws Exception {
        getMockEndpoint("mock:line").expectedBodiesReceived("C", "D", "E");

        List<Object> data = new ArrayList<Object>();
        data.add("A");
        data.add("B");
        data.add("C");
        data.add("D");
        data.add("E");
        template.sendBody("direct:start", data);

        assertMockEndpointsSatisfied();
    }

    public void testSplitterEmpty() throws Exception {
        getMockEndpoint("mock:line").expectedMessageCount(0);

        List<Object> data = new ArrayList<Object>();
        data.add("A");
        data.add("B");
        template.sendBody("direct:start", data);

        assertMockEndpointsSatisfied();
    }

    public void testSplitterEmptyAgain() throws Exception {
        getMockEndpoint("mock:line").expectedMessageCount(0);

        List<Object> data = new ArrayList<Object>();
        data.add("A");
        template.sendBody("direct:start", data);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .split(simple("${skip(2)}"))
                        .to("mock:line");
            }
        };
    }
}
