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

import java.util.Comparator;
import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.ObjectHelper;
import org.junit.Test;

public class SortExpressionTest extends ContextTestSupport {

    @Test
    public void testSortBody() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBody("direct:start", "Hadrian,William,Claus");

        assertMockEndpointsSatisfied();

        List<?> list = mock.getExchanges().get(0).getIn().getBody(List.class);
        assertEquals("Claus", list.get(0));
        assertEquals("Hadrian", list.get(1));
        assertEquals("William", list.get(2));
    }

    @Test
    public void testSortReverse() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBody("direct:reverse", "Hadrian,William,Claus");

        assertMockEndpointsSatisfied();

        List<?> list = mock.getExchanges().get(0).getIn().getBody(List.class);
        assertEquals("William", list.get(0));
        assertEquals("Hadrian", list.get(1));
        assertEquals("Claus", list.get(2));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // START SNIPPET: e1
                from("direct:start").sort(body().tokenize(",")).to("mock:result");
                // END SNIPPET: e1

                // START SNIPPET: e2
                from("direct:reverse").sort(body().tokenize(","), new MyReverseComparator()).to("mock:result");
                // END SNIPPET: e2
            }
        };
    }

    // START SNIPPET: e3
    public static class MyReverseComparator implements Comparator<Object> {

        // must have default constructor when used by spring bean testing
        public MyReverseComparator() {
        }

        @Override
        public int compare(Object o1, Object o2) {
            // just reverse it for unit testing
            return ObjectHelper.compare(o1, o2) * -1;
        }
    }
    // END SNIPPET: e3
}
