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
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class OnCompletionParallelProcessingTest extends ContextTestSupport {

    @Test
    public void testOnCompletionParallel() throws Exception {
        getMockEndpoint("mock:input").expectedBodiesReceived("World");
        getMockEndpoint("mock:after").expectedBodiesReceived("I was here Hello World");

        String out = template.requestBody("seda:bar", "World", String.class);
        assertEquals("Hello World", out);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:bar").onCompletion().parallelProcessing().transform(body().prepend("I was here ")).to("mock:after").end().to("mock:input")
                    .transform(body().prepend("Hello ")).to("log:bar");
            }
        };
    }
}
