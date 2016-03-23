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
package org.apache.camel.processor.onexception;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;

public class RedeliverEntireRouteTest extends ContextTestSupport {

    public void testRedeliverEntireRoute() throws Exception {
        getMockEndpoint("mock:a").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedMessageCount(0);
        getMockEndpoint("mock:b").expectedMessageCount(3 + 1);

        try {
            template.sendBody("direct:start", "Hello World");
            fail("Should fail");
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
                onException(IllegalArgumentException.class)
                        .maximumRedeliveries(3).redeliveryDelay(0);

                from("direct:start")
                    .to("mock:a")
                    // this route has error handler, so any exception will redeliver (eg calling the foo route again)
                    .to("direct:foo")
                    .to("mock:result");

                // this route has no error handler, so any exception will not be redelivered
                from("direct:foo")
                    .errorHandler(noErrorHandler())
                    .log("Calling foo route redelivery count: ${header.CamelRedeliveryCounter}")
                    .to("mock:b")
                    .throwException(new IllegalArgumentException("Forced"));
            }
        };
    }
}
