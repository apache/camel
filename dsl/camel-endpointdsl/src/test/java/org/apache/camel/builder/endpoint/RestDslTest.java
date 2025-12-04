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

package org.apache.camel.builder.endpoint;

import org.apache.camel.CamelContext;
import org.apache.camel.component.platform.http.vertx.VertxPlatformHttpServer;
import org.apache.camel.component.platform.http.vertx.VertxPlatformHttpServerConfiguration;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.Test;

public class RestDslTest extends BaseEndpointDslTest {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        int port = AvailablePortFinder.getNextAvailable();
        VertxPlatformHttpServerConfiguration conf = new VertxPlatformHttpServerConfiguration();
        conf.setBindPort(port);

        CamelContext context = super.createCamelContext();
        context.addService(new VertxPlatformHttpServer(conf));
        return context;
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testRestDsl() throws Exception {
        context.start();

        context.addRoutes(new EndpointRouteBuilder() {
            @Override
            public void configure() throws Exception {
                rest("/api").get("name").to(direct("username").advanced().lazyStartProducer(true));

                from(direct("username")).setBody(constant("scott"));
            }
        });

        context.stop();
    }
}
