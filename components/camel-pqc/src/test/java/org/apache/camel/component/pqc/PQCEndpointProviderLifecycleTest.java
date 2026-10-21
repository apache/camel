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
package org.apache.camel.component.pqc;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.junit.jupiter.api.Test;

import static java.security.Security.getProvider;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The BouncyCastle providers live in the JVM-global {@link java.security.Security} registry, so a PQC endpoint must
 * never remove them on stop - other endpoints, other CamelContexts and unrelated code in the same JVM rely on them.
 */
public class PQCEndpointProviderLifecycleTest {

    @Test
    void stoppingTheContextKeepsTheProvidersRegistered() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:sign").to("pqc:sign?operation=sign&signatureAlgorithm=MLDSA");
                }
            });
            context.start();

            assertNotNull(getProvider(BouncyCastleProvider.PROVIDER_NAME), "BC must be registered while running");
            assertNotNull(getProvider(BouncyCastlePQCProvider.PROVIDER_NAME), "BCPQC must be registered while running");

            context.stop();

            assertNotNull(getProvider(BouncyCastleProvider.PROVIDER_NAME),
                    "stopping a PQC endpoint must not de-register the JVM-global BC provider");
            assertNotNull(getProvider(BouncyCastlePQCProvider.PROVIDER_NAME),
                    "stopping a PQC endpoint must not de-register the JVM-global BCPQC provider");
        }
    }

    @Test
    void stoppingOneEndpointLeavesTheProviderForTheOther() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:a").routeId("a").to("pqc:a?operation=sign&signatureAlgorithm=MLDSA");
                    from("direct:b").routeId("b").to("pqc:b?operation=sign&signatureAlgorithm=MLDSA");
                }
            });
            context.start();

            // stopping the first route disposes its endpoint. The second route keeps its own Signature either way,
            // so what this pins is the provider: on main the stop de-registers it for the whole JVM.
            context.getRouteController().stopRoute("a");
            context.removeEndpoints("pqc:a*");

            context.createProducerTemplate().sendBody("direct:b", "Hello");

            assertNotNull(getProvider(BouncyCastleProvider.PROVIDER_NAME),
                    "a sibling PQC endpoint must still see the BC provider");
        }
    }
}
