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

import java.util.Iterator;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class SplitterStreamingWithErrorHandlerTest extends ContextTestSupport {

    @Test
    public void testSplitterStreamingWithError() throws Exception {
        getMockEndpoint("mock:b").expectedMessageCount(0);
        getMockEndpoint("mock:error").expectedMessageCount(1);

        // we do not stop on exception and thus the splitted message which
        // failed
        // would be silently ignored so we can continue routing
        // you can always use a custom aggregation strategy to deal with errors
        // your-self
        template.sendBody("direct:start", new Iterator<String>() {

            @Override
            public void remove() {
            }

            @Override
            public boolean hasNext() {
                return true;
            }

            @Override
            public String next() {
                throw new RuntimeException("Uhoh.");
            }

        });

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:error"));
                from("direct:start").split(body()).streaming().to("mock:b").end();
            }
        };
    }
}
