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

package org.apache.camel.issues;

import java.io.ByteArrayInputStream;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

public class CacheInputStreamInDeadLetterIssue520Test extends ContextTestSupport {
    private int count;

    public void testException() throws Exception {
        count = 0;
        MockEndpoint mock = getMockEndpoint("mock:error");
        mock.expectedMessageCount(1);

        template.sendBody("direct:start", new ByteArrayInputStream("Hello from Willem".getBytes()));
        assertEquals("The message should be delivered 4 times", count, 4);
        mock.assertIsSatisfied();

    }


    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("direct:errorHandler").maximumRedeliveries(3));
                from("direct:start").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        count++;
                        // Read the inputstream from cache
                        String result = exchange.getIn().getBody(String.class);
                        assertEquals("Should read the inputstream out again", result, "Hello from Willem");
                        throw new Exception("Forced exception by unit test");
                    }
                });

                from("direct:errorHandler").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        String result = exchange.getIn().getBody(String.class);
                        assertEquals("Should read the inputstream out again", result, "Hello from Willem");
                    }
                }).to("mock:error");

            }
        };
    }

}
