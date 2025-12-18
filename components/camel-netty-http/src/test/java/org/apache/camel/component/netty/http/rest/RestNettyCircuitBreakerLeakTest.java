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
package org.apache.camel.component.netty.http.rest;

import io.netty.util.ResourceLeakDetector;
import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.netty.http.BaseNettyTestSupport;
import org.apache.camel.component.netty.http.RestNettyHttpBinding;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RestNettyCircuitBreakerLeakTest extends BaseNettyTestSupport {

    @BindToRegistry("mybinding")
    private RestNettyHttpBinding binding = new RestNettyHttpBinding();

    @Test
    public void testCircuitBreaker() {
        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.PARANOID);

        String out = template.requestBody("netty-http:http://localhost:{{port}}/demo/get", null, String.class);
        assertEquals("demo page", out);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.PARANOID);

                // configure to use netty-http on localhost with the given port
                restConfiguration().component("netty-http").host("localhost").port(getPort())
                        .endpointProperty("nettyHttpBinding", "#mybinding");

                rest().get("/demo").produces("text/plain").to("direct:demo");
                from("direct:demo").transform().constant("demo page");

                rest().get("/demo/get").to("direct:get");
                from("direct:get")
                    .circuitBreaker()
                        .resilience4jConfiguration().timeoutEnabled(true).timeoutDuration(10000).end()
                            .log("incoming request")
                            .to("rest:get:demo?host=localhost:" + getPort())
                        .onFallback()
                            .transform().constant("timeout")
                    .end();
            }
        };
    }

}
