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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.interceptor.Tracer;

/**
 * @version 
 */
public class TracePerRouteTest extends ContextTestSupport {

    public void testTracingPerRoute() throws Exception {
        getMockEndpoint("mock:a").expectedMessageCount(1);
        getMockEndpoint("mock:b").expectedMessageCount(1);
        getMockEndpoint("mock:c").expectedMessageCount(1);

        // only a and c has enabled tracing
        MockEndpoint traced = getMockEndpoint("mock:traced");
        traced.expectedMessageCount(2);
        traced.message(0).body(String.class).contains("mock://a");
        traced.message(1).body(String.class).contains("mock://c");

        template.sendBody("direct:a", "Hello World");
        template.sendBody("direct:b", "Bye World");
        template.sendBody("direct:c", "Gooday World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                Tracer tracer = new Tracer();
                tracer.setDestinationUri("mock:traced");
                tracer.setLogName("foo");
                context.addInterceptStrategy(tracer);

                from("direct:a").to("mock:a");

                from("direct:b").noTracing().to("mock:b");

                from("direct:c").tracing().to("mock:c");
            }
        };
    }
}
