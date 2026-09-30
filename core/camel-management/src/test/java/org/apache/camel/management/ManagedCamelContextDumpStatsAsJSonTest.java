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
package org.apache.camel.management;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisabledOnOs(OS.AIX)
public class ManagedCamelContextDumpStatsAsJSonTest extends ManagementTestSupport {

    private final CountDownLatch latch = new CountDownLatch(1);

    @Test
    public void testRouteExchangesInflight() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = getContextObjectName();

        getMockEndpoint("mock:foo").expectedMessageCount(1);
        getMockEndpoint("mock:bar").expectedMessageCount(1);

        // the exchange in route foo waits on the latch, so it is inflight while the stats are dumped
        template.asyncSendBody("direct:start", "Hello World");
        template.sendBody("direct:bar", "Bye World");
        await().atMost(10, TimeUnit.SECONDS).until(() -> context.getInflightRepository().size("foo") == 1);

        try {
            String json = (String) mbeanServer.invoke(on, "dumpRouteStatsAsJSon", new Object[] { false, true },
                    new String[] { "boolean", "boolean" });
            log.info(json);

            JsonObject root = (JsonObject) Jsoner.deserialize(json);
            assertNotNull(root);
            assertEquals(1, root.getInteger("exchangesInflight"));

            JsonArray routes = (JsonArray) root.getCollection("routes");
            assertEquals(2, routes.size());
            for (Object o : routes) {
                JsonObject route = (JsonObject) o;
                int expected = "foo".equals(route.getString("id")) ? 1 : 0;
                assertEquals(expected, route.getInteger("exchangesInflight"), "route " + route.getString("id"));
            }
        } finally {
            latch.countDown();
        }

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("foo")
                        .process(e -> latch.await(20, TimeUnit.SECONDS)).id("a")
                        .to("mock:foo").id("b");

                from("direct:bar").routeId("bar")
                        .to("mock:bar").id("c");
            }
        };
    }

}
