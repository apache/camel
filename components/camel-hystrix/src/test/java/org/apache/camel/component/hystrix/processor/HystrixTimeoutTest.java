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
package org.apache.camel.component.hystrix.processor;

import java.util.concurrent.TimeoutException;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * Hystrix using timeout with Java DSL
 */
public class HystrixTimeoutTest extends CamelTestSupport {

    @Test
    public void testFast() throws Exception {
        // this calls the fast route and therefore we get a response
        Object out = template.requestBody("direct:start", "fast");
        assertEquals("Fast response", out);
    }

    @Test
    public void testSlow() throws Exception {
        // this calls the slow route and therefore causes a timeout which triggers an exception
        try {
            template.requestBody("direct:start", "slow");
            fail("Should fail due timeout");
        } catch (Exception e) {
            // expected a timeout
            assertIsInstanceOf(TimeoutException.class, e.getCause().getCause());
        }
    }

    @Test
    public void testSlowLoop() throws Exception {
        // this calls the slow route and therefore causes a timeout which triggers an exception
        for (int i = 0; i < 10; i++) {
            try {
                log.info(">>> test run " + i + " <<<");
                template.requestBody("direct:start", "slow");
                fail("Should fail due timeout");
            } catch (Exception e) {
                // expected a timeout
                assertIsInstanceOf(TimeoutException.class, e.getCause().getCause());
            }
        }
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .hystrix()
                        // use 2 second timeout
                        .hystrixConfiguration().executionTimeoutInMilliseconds(2000).end()
                        .log("Hystrix processing start: ${threadName}")
                        .toD("direct:${body}")
                        .log("Hystrix processing end: ${threadName}")
                    .end()
                    .log("After Hystrix ${body}");

                from("direct:fast")
                    // this is a fast route and takes 1 second to respond
                    .log("Fast processing start: ${threadName}")
                    .delay(1000)
                    .transform().constant("Fast response")
                    .log("Fast processing end: ${threadName}");

                from("direct:slow")
                    // this is a slow route and takes 3 second to respond
                    .log("Slow processing start: ${threadName}")
                    .delay(3000)
                    .transform().constant("Slow response")
                    .log("Slow processing end: ${threadName}");
            }
        };
    }

}
