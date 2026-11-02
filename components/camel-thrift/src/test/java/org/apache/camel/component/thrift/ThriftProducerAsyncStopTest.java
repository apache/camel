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
package org.apache.camel.component.thrift;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The asynchronous producer (the default, synchronous=false) creates a TAsyncClientManager (a selector thread and a
 * selector) on every start; stopping the producer must stop it.
 */
class ThriftProducerAsyncStopTest extends ThriftProducerBaseTest {

    @Test
    void routeRestartsStopTheClientManagersOfTheStoppedProducers() throws Exception {
        Set<Thread> before = Thread.getAllStackTraces().keySet();
        for (int i = 0; i < 5; i++) {
            context.getRouteController().stopRoute("r");
            context.getRouteController().startRoute("r");
            // the asynchronous producer of the restarted route works
            assertNull(template.requestBody("direct:thrift-ping", (Object) null));
        }
        context.getRouteController().stopRoute("r");

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            long alive = Thread.getAllStackTraces().keySet().stream()
                    .filter(t -> !before.contains(t) && t.isAlive()
                            && t.getName().startsWith("TAsyncClientManager#SelectorThread"))
                    .count();
            assertEquals(0, alive, "selector threads of stopped asynchronous producers");
        });
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:thrift-ping").routeId("r")
                        .to("thrift://localhost:" + thriftTestPort
                            + "/org.apache.camel.component.thrift.generated.Calculator?method=ping");
            }
        };
    }
}
