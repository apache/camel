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
package org.apache.camel.issues;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;

/**
 *
 */
public class SplitListListIssueTest extends ContextTestSupport {

    public void testSplitListList() throws Exception {
        List<List<String>> list = new ArrayList<List<String>>();
        for (int i = 0; i < 5; i++) {
            List<String> entry = new ArrayList<String>();
            entry.add("number" + i);
            entry.add("Camel");
            list.add(entry);
        }

        getMockEndpoint("mock:a").expectedMessageCount(5);
        getMockEndpoint("mock:b").expectedMessageCount(5);
        getMockEndpoint("mock:c").expectedMessageCount(5);
        getMockEndpoint("mock:d").expectedMessageCount(5);
        getMockEndpoint("mock:e").expectedMessageCount(5);

        template.sendBody("direct:start", list);

        assertMockEndpointsSatisfied();

        for (int i = 0; i < 5; i++) {
            assertTrue(getMockEndpoint("mock:a").getReceivedExchanges().get(i).getIn().getBody(List.class).get(0).equals("number" + i));
            assertTrue(getMockEndpoint("mock:a").getReceivedExchanges().get(i).getIn().getBody(List.class).get(1).equals("Camel"));
            assertTrue(getMockEndpoint("mock:b").getReceivedExchanges().get(i).getIn().getBody(List.class).get(0).equals("number" + i));
            assertTrue(getMockEndpoint("mock:b").getReceivedExchanges().get(i).getIn().getBody(List.class).get(1).equals("Camel"));
            assertTrue(getMockEndpoint("mock:c").getReceivedExchanges().get(i).getIn().getBody(List.class).get(0).equals("number" + i));
            assertTrue(getMockEndpoint("mock:c").getReceivedExchanges().get(i).getIn().getBody(List.class).get(1).equals("Camel"));
            assertTrue(getMockEndpoint("mock:d").getReceivedExchanges().get(i).getIn().getBody(List.class).get(0).equals("number" + i));
            assertTrue(getMockEndpoint("mock:d").getReceivedExchanges().get(i).getIn().getBody(List.class).get(1).equals("Camel"));
            assertTrue(getMockEndpoint("mock:e").getReceivedExchanges().get(i).getIn().getBody(List.class).get(0).equals("number" + i));
            assertTrue(getMockEndpoint("mock:e").getReceivedExchanges().get(i).getIn().getBody(List.class).get(1).equals("Camel"));
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .split(body())
                        .to("direct:foo")
                    .end();

                from("direct:foo")
                    .to("mock:a")
                    .to("mock:b")
                    .to("mock:c")
                    .to("mock:d")
                    .to("mock:e");
            }
        };
    }
}
