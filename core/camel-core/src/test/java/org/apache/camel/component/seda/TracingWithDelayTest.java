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
package org.apache.camel.component.seda;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TracingWithDelayTest extends ContextTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(TracingWithDelayTest.class);

    @Test
    public void testTracingWithDelay() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye World");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                getContext().setTracing(true);

                from("direct:start").delay(10).to("mock:a").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        LOG.info("This is the processor being invoked between mock:a and mock:b");
                    }
                }).to("mock:b").toD("direct:c").to("mock:result").transform(simple("${body}${body}"));

                from("direct:c").transform(constant("Bye World")).to("mock:c");
            }
        };
    }
}
