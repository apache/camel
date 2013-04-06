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
package org.apache.camel.component.vm;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;

/**
 * @version 
 */
public class VmWaitForTaskIfReplyExpectedTest extends AbstractVmTestSupport {

    public void testInOut() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye World");

        String out = template2.requestBody("direct:start", "Hello World", String.class);
        assertEquals("Bye World", out);

        assertMockEndpointsSatisfied();
    }

    public void testInOnly() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye World");

        Exchange out = template2.send("direct:start", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("Hello World");
                exchange.setPattern(ExchangePattern.InOnly);
            }
        });
        // we do not expecy a reply and thus do no wait so we just get our own input back
        assertEquals("Hello World", out.getIn().getBody());
        assertNull(out.getOut().getBody());

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("vm:foo?waitForTaskToComplete=IfReplyExpected").transform(constant("Bye World")).to("mock:result");
            }
        };
    }
    
    @Override
    protected RouteBuilder createRouteBuilderForSecondContext() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("vm:foo?waitForTaskToComplete=IfReplyExpected");
            }
        };
    }
}