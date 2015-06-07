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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;

/**
 * @version 
 */
public class SplitStopOnExceptionIssueTest extends ContextTestSupport {

    public void testSplit() throws Exception {
        getMockEndpoint("mock:line").expectedBodiesReceived("Hello", "World", "Kaboom");
        getMockEndpoint("mock:line").allMessages().exchangeProperty("foo").isEqualTo("changed");

        getMockEndpoint("mock:result").expectedMessageCount(0);

        Exchange out = template.request("direct:start", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("Hello,World,Kaboom");
            }
        });
        assertNotNull(out);
        assertTrue(out.isFailed());
        assertFalse(out.hasOut());

        // when we use stopOnException the exchange should not be affected during the splitter
        // eg the foo property should have the before value
        assertEquals("before", out.getProperty("foo"));
        assertEquals("Hello,World,Kaboom", out.getIn().getBody());

        IllegalArgumentException iae = out.getException(IllegalArgumentException.class);
        assertNotNull(iae);
        assertEquals("Forced exception", iae.getMessage());

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .setProperty("foo", constant("before"))
                    .split().tokenize(",")
                        .setProperty("foo", constant("changed"))
                        .to("mock:line")
                        .filter(body().contains("Kaboom"))
                            .throwException(new IllegalArgumentException("Forced exception"))
                        .end()
                    .end()
                    .to("mock:result");
            }
        };
    }
}
