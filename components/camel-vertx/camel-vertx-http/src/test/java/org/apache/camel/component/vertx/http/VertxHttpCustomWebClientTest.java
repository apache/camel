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
package org.apache.camel.component.vertx.http;

import io.vertx.ext.web.client.WebClientOptions;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VertxHttpCustomWebClientTest {

    @Test
    public void testCustomWebClientOptions() throws Exception {
        WebClientOptions opts = new WebClientOptions();
        opts.setSsl(true);
        opts.setConnectTimeout(Integer.MAX_VALUE);

        try (CamelContext context = new DefaultCamelContext()) {
            VertxHttpComponent component = new VertxHttpComponent();
            component.setCamelContext(context);
            component.setWebClientOptions(opts);

            context.start();

            assertSame(opts, component.getWebClientOptions());
            assertTrue(component.getWebClientOptions().isSsl());
            assertEquals(Integer.MAX_VALUE, component.getWebClientOptions().getConnectTimeout());
        }
    }

    @Test
    public void testCustomWebClientOptionsWithRoute() throws Exception {
        WebClientOptions opts = new WebClientOptions();
        opts.setSsl(true);
        opts.setConnectTimeout(Integer.MAX_VALUE);

        try (CamelContext context = new DefaultCamelContext()) {
            VertxHttpComponent component = new VertxHttpComponent();
            component.setWebClientOptions(opts);

            context.addComponent("vertx-http", component);
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:start")
                            .to("vertx-http:http://localhost:8080");
                }
            });

            context.start();

            VertxHttpEndpoint ve = context.getEndpoint("vertx-http:http://localhost:8080", VertxHttpEndpoint.class);
            assertTrue(ve.getConfiguration().getWebClientOptions().isSsl());
            assertEquals(Integer.MAX_VALUE, ve.getConfiguration().getWebClientOptions().getConnectTimeout());
        }
    }
}
