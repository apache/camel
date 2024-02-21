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
package org.apache.camel.component.platform.http.vertx;

import java.util.concurrent.TimeUnit;

import io.restassured.RestAssured;
import org.apache.camel.CamelContext;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.platform.http.PlatformHttpComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.engine.PooledExchangeFactory;
import org.apache.camel.impl.engine.PooledProcessorExchangeFactory;
import org.apache.camel.spi.PooledObjectFactory;
import org.apache.camel.test.AvailablePortFinder;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.get;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class VertxPlatformHttpPooledExchangeTest {

    @Test
    public void testEngineSetup() throws Exception {
        final CamelContext context = createCamelContext();
        try {
            context.start();

            assertThat(VertxPlatformHttpRouter.lookup(context)).isNotNull();
            assertThat(context.getComponent("platform-http")).isInstanceOfSatisfying(PlatformHttpComponent.class, component -> {
                assertThat(component.getEngine()).isInstanceOf(VertxPlatformHttpEngine.class);
            });

        } finally {
            context.stop();
        }
    }

    @Test
    public void testPooledExchange() throws Exception {
        final CamelContext context = createCamelContext();

        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("platform-http:/vertx/pooled")
                            .transform().simple("Bye World");
                }
            });

            context.start();

            for (int i = 0; i < 3; i++) {
                get("/vertx/pooled")
                        .then()
                        .statusCode(200)
                        .body(is("Bye World"));
            }

            MockEndpoint.assertIsSatisfied(context);

            Awaitility.waitAtMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
                PooledObjectFactory.Statistics stat
                        = context.getCamelContextExtension().getExchangeFactoryManager().getStatistics();
                assertEquals(1, stat.getCreatedCounter());
                assertEquals(2, stat.getAcquiredCounter());
                assertEquals(3, stat.getReleasedCounter());
                assertEquals(0, stat.getDiscardedCounter());
            });
        } finally {
            context.stop();
        }
    }

    static CamelContext createCamelContext() throws Exception {
        return createCamelContext(null);
    }

    private static CamelContext createCamelContext(ServerConfigurationCustomizer customizer) throws Exception {
        int port = AvailablePortFinder.getNextAvailable();
        VertxPlatformHttpServerConfiguration conf = new VertxPlatformHttpServerConfiguration();
        conf.setBindPort(port);

        RestAssured.port = port;

        if (customizer != null) {
            customizer.customize(conf);
        }

        CamelContext context = new DefaultCamelContext();
        context.addService(new VertxPlatformHttpServer(conf));

        ExtendedCamelContext ecc = context.getCamelContextExtension();

        ecc.setExchangeFactory(new PooledExchangeFactory());
        ecc.setProcessorExchangeFactory(new PooledProcessorExchangeFactory());
        ecc.getExchangeFactory().setStatisticsEnabled(true);
        ecc.getProcessorExchangeFactory().setStatisticsEnabled(true);

        return context;
    }

    interface ServerConfigurationCustomizer {
        void customize(VertxPlatformHttpServerConfiguration configuration);
    }
}
