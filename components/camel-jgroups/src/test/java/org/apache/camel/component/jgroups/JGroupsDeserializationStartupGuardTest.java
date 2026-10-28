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
import org.jgroups.JChannel;
import org.jgroups.protocols.AUTH;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the consumer start-up guard: a consumer on the default (unauthenticated) channel refuses to start when no
 * pre-read deserialization control is configured, and starts once one is present ({@code acceptAllObjects=true}, the
 * {@code jgroups.deserialization.filter} system property, or a channel secured with AUTH/encryption).
 */
class JGroupsDeserializationStartupGuardTest {

    private static final String FILTER_PROPERTY = "jgroups.deserialization.filter";

    @Test
    void consumerFailsFastWithoutPreReadControl() throws Exception {
        System.clearProperty(FILTER_PROPERTY);
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

    @Test
    void consumerStartsWithJGroupsDeserializationFilterProperty() throws Exception {
        System.setProperty(FILTER_PROPERTY, "*");
        try (CamelContext context = new DefaultCamelContext()) {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("jgroups:guardPropCluster").to("mock:out");
                }
            });
            context.start();
            assertTrue(context.getStatus().isStarted(),
                    "Context should have started with jgroups.deserialization.filter set");
        } finally {
            System.clearProperty(FILTER_PROPERTY);
        }
    }

    @Test
    void securedChannelIsDetected() throws Exception {
        try (JChannel plain = new JChannel()) {
            assertFalse(JGroupsEndpoint.isSecuredChannel(plain), "A default channel is not secured");
            plain.getProtocolStack().addProtocol(new AUTH());
            assertTrue(JGroupsEndpoint.isSecuredChannel(plain), "A channel with AUTH in its stack is secured");
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
