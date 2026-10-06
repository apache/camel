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
package org.apache.camel.component.undertow.handlers;

import org.apache.camel.CamelContext;
import org.apache.camel.component.undertow.UndertowEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The security settings that a WebSocket producer configures for itself are not used when a consumer is on the path,
 * which is reported when they differ from the settings of the consumer.
 */
class CamelWebSocketHandlerSecuritySettingsTest {

    @Test
    void producerWithoutItsOwnSettings() throws Exception {
        assertFalse(hasUnusedSecuritySettings("allowedRoles=user", "sendToAll=true"));
    }

    @Test
    void producerWithTheSameSettingsAsTheConsumer() throws Exception {
        assertFalse(hasUnusedSecuritySettings("allowedRoles=admin", "allowedRoles=admin"));
    }

    @Test
    void producerWithOtherRolesThanTheConsumer() throws Exception {
        assertTrue(hasUnusedSecuritySettings("allowedRoles=user", "allowedRoles=admin"));
    }

    @Test
    void producerWithRolesOnAPathWhoseConsumerHasNone() throws Exception {
        assertTrue(hasUnusedSecuritySettings("fireWebSocketChannelEvents=true", "allowedRoles=admin"));
    }

    private static boolean hasUnusedSecuritySettings(String consumerOptions, String producerOptions) throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.start();
            UndertowEndpoint consumerEndpoint
                    = context.getEndpoint("undertow:ws://localhost:8080/path?" + consumerOptions, UndertowEndpoint.class);
            UndertowEndpoint producerEndpoint
                    = context.getEndpoint("undertow:ws://localhost:8080/path?" + producerOptions, UndertowEndpoint.class);
            return CamelWebSocketHandler.hasUnusedSecuritySettings(consumerEndpoint, producerEndpoint);
        }
    }
}
