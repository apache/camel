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
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.aggregate.UseLatestAggregationStrategy;
import org.junit.Test;

public class AggregatorExceptionTest extends ContextTestSupport {

    @Test
    public void testAggregateAndOnException() throws Exception {
        // all goes to error
        MockEndpoint mock = getMockEndpoint("mock:error");
        mock.expectedMessageCount(2);

        for (int c = 0; c <= 10; c++) {
            template.sendBodyAndHeader("direct:start", "Hi!" + c, "id", 123);
        }

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                final String exceptionString = "This is an Error not an Exception";
                errorHandler(deadLetterChannel("mock:error"));

                from("direct:start")
                    .aggregate(header("id"), new UseLatestAggregationStrategy())
                    .completionSize(5)
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            throw new java.lang.NoSuchMethodError(exceptionString);   
                        }
                    });
            }
        };
    }
}
