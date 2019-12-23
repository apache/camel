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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

/**
 *
 */
public class SplitterOnPrepareExceptionTest extends ContextTestSupport {

    @Test
    public void testSplitterOnPrepare() throws Exception {
        getMockEndpoint("mock:a").expectedMessageCount(2);

        String body = "Hello,Bye,Kaboom,Hi";
        try {
            template.sendBody("direct:start", body);
            fail("Should have thrown exception");
        } catch (Exception e) {
            // expected
        }

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").split(body().tokenize(",")).onPrepare(new FixNamePrepare()).stopOnException().to("mock:a");
            }
        };
    }

    public static final class FixNamePrepare implements Processor {

        @Override
        public void process(Exchange exchange) throws Exception {
            String name = exchange.getIn().getBody(String.class);
            if ("Kaboom".equals(name)) {
                throw new IllegalArgumentException("Forced error");
            }
        }
    }
}
