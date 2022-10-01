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
package org.apache.camel.component.resilience4j;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.CircuitBreakerConstants;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

public class ResilienceRouteBulkheadOkTest extends CamelTestSupport {

    @Test
    public void testResilience() throws Exception {
        test("direct:start");
    }

    @Test
    public void testResilienceWithTimeOut() throws Exception {
        test("direct:start.with.timeout.enabled");
    }

    private void test(String endPointUri) throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye World");
        getMockEndpoint("mock:result").expectedPropertyReceived(CircuitBreakerConstants.RESPONSE_SUCCESSFUL_EXECUTION, true);
        getMockEndpoint("mock:result").expectedPropertyReceived(CircuitBreakerConstants.RESPONSE_FROM_FALLBACK, false);

        template.sendBody(endPointUri, "Hello World");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").circuitBreaker().resilience4jConfiguration().bulkheadEnabled(true).end().to("direct:foo")
                        .to("log:foo").onFallback().transform()
                        .constant("Fallback message").end().to("log:result").to("mock:result");

                from("direct:start.with.timeout.enabled").circuitBreaker().resilience4jConfiguration().bulkheadEnabled(true).timeoutEnabled(true).timeoutDuration(2000).end()
                        .to("direct:foo")
                        .to("log:foo").onFallback().transform()
                        .constant("Fallback message").end().to("log:result").to("mock:result");

                from("direct:foo").transform().constant("Bye World");
            }
        };
    }

}
