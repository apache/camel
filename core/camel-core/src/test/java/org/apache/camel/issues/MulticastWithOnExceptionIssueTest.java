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
package org.apache.camel.issues;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class MulticastWithOnExceptionIssueTest extends ContextTestSupport {

    @Test
    public void testEnd1FailureTest() throws Exception {
        MockEndpoint end1 = getMockEndpoint("mock:end1");
        end1.whenAnyExchangeReceived(new Processor() {
            public void process(Exchange exchange) throws Exception {
                throw new RuntimeException("Simulated Exception");
            }
        });

        getMockEndpoint("mock:end2").expectedMessageCount(1);
        getMockEndpoint("mock:end3").expectedMessageCount(0);
        getMockEndpoint("mock:end4").expectedMessageCount(1);

        String result = template.requestBody("direct:start", "Hello World!", String.class);
        assertEquals("Stop!", result);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testEnd2FailureTest() throws Exception {
        MockEndpoint end2 = getMockEndpoint("mock:end2");
        end2.whenAnyExchangeReceived(new Processor() {
            public void process(Exchange exchange) throws Exception {
                throw new RuntimeException("Simulated Exception");
            }
        });

        getMockEndpoint("mock:end1").expectedMessageCount(1);
        getMockEndpoint("mock:end3").expectedMessageCount(0);
        getMockEndpoint("mock:end4").expectedMessageCount(1);

        String result = template.requestBody("direct:start", "Hello World!", String.class);
        assertEquals("Stop!", result);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testEnd3FailureTest() throws Exception {
        MockEndpoint end3 = getMockEndpoint("mock:end3");
        end3.whenAnyExchangeReceived(new Processor() {
            public void process(Exchange exchange) throws Exception {
                throw new RuntimeException("Simulated Exception");
            }
        });

        getMockEndpoint("mock:end1").expectedMessageCount(1);
        getMockEndpoint("mock:end2").expectedMessageCount(1);
        getMockEndpoint("mock:end4").expectedMessageCount(1);

        String result = template.requestBody("direct:start", "Hello World!", String.class);
        assertEquals("Stop!", result);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testOK() throws Exception {
        getMockEndpoint("mock:end1").expectedMessageCount(1);
        getMockEndpoint("mock:end2").expectedMessageCount(1);
        getMockEndpoint("mock:end3").expectedMessageCount(1);
        getMockEndpoint("mock:end4").expectedMessageCount(0);

        String result = template.requestBody("direct:start", "Hello World!", String.class);
        assertEquals("Hello to you too!", result);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(Exception.class).handled(true).to("log:onException").to("mock:end4").transform(constant("Stop!"));

                from("direct:start").multicast().to("mock:end1", "mock:end2").end().to("mock:end3").transform(constant("Hello to you too!"));
            }
        };
    }
}
