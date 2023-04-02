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

import org.apache.camel.CamelContext;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.BeanIntrospection;
import org.apache.camel.spi.CircuitBreakerConstants;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ResilienceRouteOkTest extends CamelTestSupport {

    private BeanIntrospection bi;

    @Override
    protected boolean useJmx() {
        return false;
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        bi = PluginHelper.getBeanIntrospection(context);
        bi.setLoggingLevel(LoggingLevel.INFO);
        bi.resetCounters();

        return context;
    }

    @Test
    public void testResilience() throws Exception {
        test("direct:start");
    }

    @Test
    public void testResilienceWithTimeOut() throws Exception {
        test("direct:start.with.timeout.enabled");
    }

    private void test(String endPointUri) throws Exception {
        assertEquals(0, bi.getInvokedCounter());

        getMockEndpoint("mock:result").expectedBodiesReceived("Bye World");
        getMockEndpoint("mock:result").expectedPropertyReceived(CircuitBreakerConstants.RESPONSE_SUCCESSFUL_EXECUTION, true);
        getMockEndpoint("mock:result").expectedPropertyReceived(CircuitBreakerConstants.RESPONSE_FROM_FALLBACK, false);

        template.sendBody(endPointUri, "Hello World");

        MockEndpoint.assertIsSatisfied(context);

        assertEquals(0, bi.getInvokedCounter());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").circuitBreaker().to("direct:foo").to("log:foo").onFallback().transform()
                        .constant("Fallback message").end().to("log:result").to("mock:result");

                from("direct:start.with.timeout.enabled").circuitBreaker().resilience4jConfiguration()
                        .timeoutEnabled(true).timeoutDuration(2000).end()
                        .to("direct:foo").to("log:foo").onFallback().transform()
                        .constant("Fallback message").end().to("log:result").to("mock:result");

                from("direct:foo").transform().constant("Bye World");
            }
        };
    }

}
