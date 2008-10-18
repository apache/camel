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
package org.apache.camel.processor.aggregator;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.interceptor.Tracer;

/**
 * Unit test with timer, splitter, aggregator and tracer.
 */
public class AggregatorTimerAndTracerTest extends ContextTestSupport {

    public void testAggregatorTimerAndTracer() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                getContext().addInterceptStrategy(new Tracer());

                from("timer://kickoff?period=9999910000").
                    setHeader("id").constant("foo").setBody().constant("a b c").
                    splitter(body().tokenize(" ")).to("seda:splitted");

                from("seda:splitted").
                    aggregator(header("id")).
                    to("mock:result");
            }
        };
    }
}
