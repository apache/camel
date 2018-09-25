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

import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

/**
 * @version 
 */
public class MessageHistoryCopyExchangeTest extends ContextTestSupport {

    @Test
    public void testDefensiveCopyOfMessageHistory() throws Exception {
        MockEndpoint a = getMockEndpoint("mock:a");
        a.expectedMessageCount(1);
        MockEndpoint b = getMockEndpoint("mock:b");
        b.expectedMessageCount(1);

        template.sendBody("seda:start", "Hello World");

        assertMockEndpointsSatisfied();

        List listA = (List) a.getReceivedExchanges().get(0).getProperty(Exchange.MESSAGE_HISTORY);
        List listB = (List) b.getReceivedExchanges().get(0).getProperty(Exchange.MESSAGE_HISTORY);

        assertNotSame(listA, listB);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.setMessageHistory(true);

                from("seda:start")
                        .to("log:foo")
                        .to("mock:a")
                        .to("direct:bar")
                        .to("mock:b");

                from("direct:bar")
                    .to("log:bar")
                    .to("mock:bar");
            }
        };
    }
}
