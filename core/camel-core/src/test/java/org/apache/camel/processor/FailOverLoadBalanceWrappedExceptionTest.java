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

import java.io.IOException;
import java.net.SocketException;

import org.apache.camel.CamelExchangeException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Before;
import org.junit.Test;

public class FailOverLoadBalanceWrappedExceptionTest extends ContextTestSupport {

    protected MockEndpoint x;
    protected MockEndpoint y;
    protected MockEndpoint z;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        x = getMockEndpoint("mock:x");
        y = getMockEndpoint("mock:y");
        z = getMockEndpoint("mock:z");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").loadBalance().failover(IOException.class).to("direct:x", "direct:y", "direct:z");

                from("direct:x").to("mock:x").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        // socket exception is an io exception
                        throw new CamelExchangeException("foo", exchange, new SocketException("Forced"));
                    }
                });

                from("direct:y").to("mock:y").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        throw new IOException("Forced");
                    }
                });

                from("direct:z").to("mock:z");
            }
        };
    }

    @Test
    public void testWrappedException() throws Exception {
        x.expectedMessageCount(1);
        y.expectedMessageCount(1);
        z.expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

}
