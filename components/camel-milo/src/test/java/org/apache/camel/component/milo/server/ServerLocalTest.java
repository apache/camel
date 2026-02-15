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
package org.apache.camel.component.milo.server;

import org.apache.camel.EndpointInject;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.milo.converter.ConverterTest;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Unit tests for milo server component without using an actual connection
 */
public class ServerLocalTest extends CamelTestSupport {

    private static final String MILO_ITEM_1 = "milo-server:myitem1";

    private static final String MOCK_TEST = "mock:test";

    private static final Logger LOG = LoggerFactory.getLogger(ConverterTest.class);

    @EndpointInject(MOCK_TEST)
    protected MockEndpoint testEndpoint;

    @BeforeEach
    public void pickFreePort(TestInfo testInfo) {
        final var displayName = testInfo.getDisplayName();
        LOG.info("********************************************************************************");
        LOG.info(displayName);
        LOG.info("********************************************************************************");
        final MiloServerComponent component = context().getComponent("milo-server", MiloServerComponent.class);
        component.setPort(AvailablePortFinder.getNextAvailable());
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(MILO_ITEM_1).to(MOCK_TEST);
            }
        };
    }

    @Test
    public void shouldStartComponent() {
    }

    @Test
    public void testAcceptVariantString() {
        Assertions.assertDoesNotThrow(() -> sendBody(MILO_ITEM_1, new Variant("Foo")));
    }

    @Test
    public void testAcceptVariantDouble() {
        Assertions.assertDoesNotThrow(() -> sendBody(MILO_ITEM_1, new Variant(0.0)));
    }

    @Test
    public void testAcceptString() {
        Assertions.assertDoesNotThrow(() -> sendBody(MILO_ITEM_1, "Foo"));
    }

    @Test
    public void testAcceptDouble() {
        Assertions.assertDoesNotThrow(() -> sendBody(MILO_ITEM_1, 0.0));
    }

    @Test
    public void testAcceptDataValueString() {
        Assertions.assertDoesNotThrow(() -> sendBody(MILO_ITEM_1, new DataValue(new Variant("Foo"))));
    }

    @Test
    public void testAcceptDataValueDouble() {
        Assertions.assertDoesNotThrow(() -> sendBody(MILO_ITEM_1, new DataValue(new Variant(0.0))));
    }
}
