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

package org.apache.camel.component.jms;

import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.awaitility.Awaitility;

/**
 * Shared helpers for JMS tests. Kept as a standalone utility so the same static API is reachable from every JMS test
 * lineage ({@link AbstractJMSTest}, {@link AbstractPersistentJMSTest} and plain {@code CamelTestSupport} tests) without
 * forcing them into a common base class.
 */
public final class JmsTestHelper {

    public static final long JMS_CONSUMER_ROUTE_UPTIME_MILLIS = 100;
    public static final long JMS_CONSUMER_ROUTE_WAIT_AT_MOST_MILLIS = 30_000;

    private JmsTestHelper() {
    }

    /**
     * Wait until the given routes have been running for {@link #JMS_CONSUMER_ROUTE_UPTIME_MILLIS} milliseconds. Route
     * uptime is only a heuristic for JMS listener registration on the broker: once a consumer route has been up for a
     * short while its listener is expected to be subscribed, so tests can send without racing an unregistered listener.
     *
     * @param context  the Camel context owning the routes
     * @param routeIds the ids of the consumer routes to wait for
     */
    public static void waitForJmsConsumerRoutes(CamelContext context, String... routeIds) {
        waitForJmsConsumerRoutes(context, JMS_CONSUMER_ROUTE_UPTIME_MILLIS, routeIds);
    }

    /**
     * Wait until the given routes have been running for at least {@code minUptimeMillis} milliseconds, giving up after
     * {@link #JMS_CONSUMER_ROUTE_WAIT_AT_MOST_MILLIS} milliseconds. A missing route (unknown id, or one already removed
     * from the context) is treated as not-yet-ready rather than throwing, so a misconfigured route id surfaces as a
     * clear timeout instead of a {@link NullPointerException}.
     *
     * @param context         the Camel context owning the routes
     * @param minUptimeMillis the minimum route uptime, in milliseconds, before a route is considered ready
     * @param routeIds        the ids of the consumer routes to wait for
     */
    public static void waitForJmsConsumerRoutes(CamelContext context, long minUptimeMillis, String... routeIds) {
        Awaitility.await().atMost(JMS_CONSUMER_ROUTE_WAIT_AT_MOST_MILLIS, TimeUnit.MILLISECONDS).until(() -> {
            for (String routeId : routeIds) {
                Route route = context.getRoute(routeId);
                if (route == null || route.getUptimeMillis() <= minUptimeMillis) {
                    return false;
                }
            }
            return true;
        });
    }
}
