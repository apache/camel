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

public class SplitterCollateTest extends ContextTestSupport {

    public void testSplitterCollate() throws Exception {
        getMockEndpoint("mock:line").expectedMessageCount(2);

        List<Object> data = new ArrayList<Object>();
        data.add("A");
        data.add("B");
        data.add("C");
        data.add("D");
        data.add("E");
        template.sendBody("direct:start", data);

        assertMockEndpointsSatisfied();

        List chunk = getMockEndpoint("mock:line").getReceivedExchanges().get(0).getIn().getBody(List.class);
        List chunk2 = getMockEndpoint("mock:line").getReceivedExchanges().get(1).getIn().getBody(List.class);

        assertEquals(3, chunk.size());
        assertEquals(2, chunk2.size());

        assertEquals("A", chunk.get(0));
        assertEquals("B", chunk.get(1));
        assertEquals("C", chunk.get(2));
        assertEquals("D", chunk2.get(0));
        assertEquals("E", chunk2.get(1));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .split(simple("${collate(3)}"))
                        .to("mock:line");
            }
        };
    }
}
