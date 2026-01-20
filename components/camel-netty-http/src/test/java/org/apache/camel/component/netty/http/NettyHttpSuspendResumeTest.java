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
package org.apache.camel.component.netty.http;

import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@DisabledOnOs(OS.WINDOWS)
public class NettyHttpSuspendResumeTest extends BaseNettyTestSupport {

    private final String serverUri
            = "netty-http:http://localhost:" + getPort() + "/cool?disconnect=true&send503whenSuspended=false";

    @Test
    public void testNettySuspendResume() {
        context.getShutdownStrategy().setTimeout(50);

        String reply = template.requestBody(serverUri, "World", String.class);
        assertEquals("Bye World", reply);

        // now suspend netty
        NettyHttpConsumer consumer = (NettyHttpConsumer) context.getRoute("foo").getConsumer();
        assertNotNull(consumer);

        // suspend
        consumer.suspend();

        try {
            template.requestBody(serverUri, "Moon", String.class);
            fail("Should throw exception");
        } catch (Exception e) {
            assertTrue(e.getCause().getMessage().startsWith("Cannot connect to localhost"));
        }

        // resume
        consumer.resume();

        await().atMost(2, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    // and send request which should be processed
                    String nextReply = template.requestBody(serverUri, "Moon", String.class);
                    assertEquals("Bye Moon", nextReply);
                });
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(serverUri).routeId("foo")
                        .transform(body().prepend("Bye "));
            }
        };
    }

}
