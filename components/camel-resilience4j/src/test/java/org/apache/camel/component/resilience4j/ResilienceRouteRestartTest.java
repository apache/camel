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

import java.util.concurrent.TimeUnit;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A circuit breaker with a timeout must still call its steps after its route is stopped and started again.
 */
public class ResilienceRouteRestartTest extends CamelTestSupport {

    @Test
    public void testSyncAfterRestart() throws Exception {
        assertEquals("Bye World", request("direct:sync"));

        context.getRouteController().stopRoute("sync");
        context.getRouteController().startRoute("sync");

        assertEquals("Bye World", request("direct:sync"));
    }

    @Test
    public void testAsyncAfterRestart() throws Exception {
        assertEquals("Bye World", request("direct:async"));

        context.getRouteController().stopRoute("async");
        context.getRouteController().startRoute("async");

        assertEquals("Bye World", request("direct:async"));
    }

    private Object request(String uri) throws Exception {
        // on a thread of the template, so a call that never completes fails the test instead of blocking it
        return template.asyncRequestBody(uri, "Hello").get(10, TimeUnit.SECONDS);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:sync").routeId("sync")
                        .circuitBreaker()
                            .resilience4jConfiguration().timeoutEnabled(true).timeoutDuration(2000).end()
                            .setBody(constant("Bye World"))
                        .onFallback()
                            .transform().constant("Fallback")
                        .end();

                from("direct:async").routeId("async")
                        .circuitBreaker()
                            .resilience4jConfiguration().asynchronous(true).timeoutEnabled(true).timeoutDuration(2000).end()
                            .setBody(constant("Bye World"))
                        .onFallback()
                            .transform().constant("Fallback")
                        .end();
            }
        };
    }
}
