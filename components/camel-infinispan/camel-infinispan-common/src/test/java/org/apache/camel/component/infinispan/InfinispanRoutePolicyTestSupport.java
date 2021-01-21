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
package org.apache.camel.component.infinispan;

import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public interface InfinispanRoutePolicyTestSupport {
    InfinispanRoutePolicy createRoutePolicy(String lockValue);

    @Test
    default void testLeadership() throws Exception {
        final InfinispanRoutePolicy policy1 = createRoutePolicy("route1");
        final InfinispanRoutePolicy policy2 = createRoutePolicy("route2");

        try (CamelContext context = new DefaultCamelContext()) {
            context.start();

            RouteBuilder.addRoutes(context, b -> b.from("direct:r1").routePolicy(policy1).to("mock:p1"));

            await().atMost(10, TimeUnit.SECONDS).until(policy1::isLeader);

            RouteBuilder.addRoutes(context, b -> b.from("direct:r2").routePolicy(policy2).to("mock:p2"));

            assertTrue(policy1.isLeader());
            assertFalse(policy2.isLeader());

            policy1.shutdown();

            await().atMost(10, TimeUnit.SECONDS).until(policy2::isLeader);

            assertFalse(policy1.isLeader());
            assertTrue(policy2.isLeader());
        }
    }
}
