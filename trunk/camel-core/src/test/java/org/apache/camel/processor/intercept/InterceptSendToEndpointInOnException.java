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
package org.apache.camel.processor.intercept;

import java.io.IOException;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version 
 */
public class InterceptSendToEndpointInOnException extends ContextTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(IOException.class)
                    .handled(true)
                    .to("mock:io");

                interceptSendToEndpoint("mock:io")
                    .skipSendToOriginalEndpoint()
                    .to("mock:intercepted");

                from("direct:start")
                    .to("mock:foo")
                    .to("mock:result");
            }
        };
    }

    public void testOk() throws Exception {
        getMockEndpoint("mock:io").expectedMessageCount(0);
        getMockEndpoint("mock:intercepted").expectedMessageCount(0);
        getMockEndpoint("mock:foo").expectedMessageCount(2);
        getMockEndpoint("mock:result").expectedMessageCount(2);

        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:start", "Bye World");

        assertMockEndpointsSatisfied();
    }

    public void testAnotherError() throws Exception {
        getMockEndpoint("mock:io").expectedMessageCount(0);
        getMockEndpoint("mock:intercepted").expectedMessageCount(0);
        getMockEndpoint("mock:foo").expectedMessageCount(2);
        getMockEndpoint("mock:result").expectedMessageCount(0);

        MockEndpoint foo = getMockEndpoint("mock:foo");
        foo.whenAnyExchangeReceived(new Processor() {
            public void process(Exchange exchange) throws Exception {
                throw new IllegalArgumentException("Forced");
            }
        });

        try {
            template.sendBody("direct:start", "Hello World");
            fail("Should have thrown exception");
        } catch (CamelExecutionException e) {
            assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
        }

        try {
            template.sendBody("direct:start", "Bye World");
            fail("Should have thrown exception");
        } catch (CamelExecutionException e) {
            assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
        }

        assertMockEndpointsSatisfied();
    }

    public void testIOError() throws Exception {
        getMockEndpoint("mock:io").expectedMessageCount(0);
        getMockEndpoint("mock:intercepted").expectedMessageCount(2);
        getMockEndpoint("mock:foo").expectedMessageCount(2);
        getMockEndpoint("mock:result").expectedMessageCount(0);

        MockEndpoint foo = getMockEndpoint("mock:foo");
        foo.whenAnyExchangeReceived(new Processor() {
            public void process(Exchange exchange) throws Exception {
                throw new IOException("Forced");
            }
        });

        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:start", "Bye World");

        assertMockEndpointsSatisfied();
    }

}
