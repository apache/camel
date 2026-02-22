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
package org.apache.camel.component.hazelcast;

import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HazelcastErrorMessagesTest extends HazelcastCamelTestSupport {

    @Test
    public void testAtomicNumberConsumer() {
        RouteBuilder builder = new RouteBuilder() {
            public void configure() throws Exception {
                from("hazelcast-atomicvalue:foo").to("seda:out");
            }
        };
        Exception e = assertThrows(Exception.class, () -> {
            context.addRoutes(builder);
            context.start();
        });
        assertTrue(e.getCause().getMessage()
                .contains("You cannot send messages to this endpoint: hazelcast-atomicvalue://foo"));
    }

    @Test
    public void testPNCounterConsumer() {
        RouteBuilder builder = new RouteBuilder() {
            public void configure() throws Exception {
                from("hazelcast-pncounter:foo").to("seda:out");
            }
        };
        Exception e = assertThrows(Exception.class, () -> {
            context.addRoutes(builder);
            context.start();
        });
        assertTrue(e.getCause().getMessage()
                .contains("You cannot send messages to this endpoint: hazelcast-pncounter://foo"));
    }

    @Test
    public void testInstanceProducer() {
        RouteBuilder builder = new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:foo").to("hazelcast-instance:foo");
            }
        };

        Exception e = assertThrows(Exception.class, () -> {
            context.addRoutes(builder);
            context.start();
        });
        assertTrue(
                e.getCause().getMessage().contains("You cannot send messages to this endpoint: hazelcast-instance://foo"));
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

}
