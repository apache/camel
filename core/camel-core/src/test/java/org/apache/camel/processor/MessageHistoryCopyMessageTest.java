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

import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.MessageHistory;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class MessageHistoryCopyMessageTest extends ContextTestSupport {

    @Test
    public void testCopyMessage() throws Exception {
        getMockEndpoint("mock:a").expectedMessageCount(1);
        getMockEndpoint("mock:b").expectedMessageCount(1);
        getMockEndpoint("mock:bar").expectedMessageCount(1);

        Exchange out = template.request("direct:start", e -> {
            e.getMessage().setBody("Hello World");
        });

        assertMockEndpointsSatisfied();

        // only the step eips are in the history
        List<MessageHistory> history = out.getProperty(Exchange.MESSAGE_HISTORY, List.class);
        assertNotNull(history);
        assertEquals(3, history.size());
        assertEquals("step", history.get(0).getNode().getShortName());
        assertEquals("a", history.get(0).getNode().getId());
        assertEquals("Hello World", history.get(0).getMessage().getBody());
        assertEquals("step", history.get(1).getNode().getShortName());
        assertEquals("b", history.get(1).getNode().getId());
        assertEquals("Bye World", history.get(1).getMessage().getBody());
        assertEquals("step", history.get(2).getNode().getShortName());
        assertEquals("bar", history.get(2).getNode().getId());
        assertEquals("Hi World", history.get(2).getMessage().getBody());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.setMessageHistory(true);
                context.getMessageHistoryFactory().setNodePattern("step");
                context.getMessageHistoryFactory().setCopyMessage(true);

                from("direct:start").step("a").transform().constant("Bye World").to("mock:a").end().step("b").transform().constant("Hi World").to("direct:bar").to("mock:b").end();

                from("direct:bar").step("bar").to("log:bar").to("mock:bar").end();
            }
        };
    }
}
