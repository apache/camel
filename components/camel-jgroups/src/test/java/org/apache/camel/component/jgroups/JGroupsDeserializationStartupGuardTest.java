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
package org.apache.camel.component.jgroups;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the consumer start-up guard: a consumer on the default (unauthenticated) channel refuses to start when no
 * pre-read deserialization control is configured, and starts once {@code acceptAllObjects=true} opts out of the guard.
 */
class JGroupsDeserializationStartupGuardTest {

    @Test
    void consumerFailsFastWithoutPreReadControl() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("jgroups:guardFailCluster").to("mock:out");
                }
            });
            Exception ex = assertThrows(Exception.class, context::start);
            assertTrue(hasGuardFailure(ex),
                    "Expected the start-up guard to refuse the consumer, but was: " + ex);
        }
    }

    @Test
    void consumerStartsWhenAcceptAllObjects() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("jgroups:guardOptInCluster?acceptAllObjects=true").to("mock:out");
                }
            });
            context.start();
            assertTrue(context.getStatus().isStarted(), "Context should have started with acceptAllObjects=true");
        }
    }

    private static boolean hasGuardFailure(Throwable t) {
        for (Throwable cause = t; cause != null; cause = cause.getCause()) {
            if (cause instanceof JGroupsException && cause.getMessage() != null
                    && cause.getMessage().contains("acceptAllObjects")) {
                return true;
            }
        }
        return false;
    }

}
