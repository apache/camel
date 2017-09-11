/**
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
package org.apache.camel.component.atomix.client.value;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import io.atomix.variables.DistributedValue;
import org.apache.camel.Component;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.atomix.client.AtomixClientConstants;
import org.apache.camel.component.atomix.client.AtomixClientTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class AtomixValueConsumerTest extends AtomixClientTestSupport {
    private static final String VALUE_NAME = UUID.randomUUID().toString();
    private DistributedValue<Object> value;

    // ************************************
    // Setup
    // ************************************

    @Override
    protected Map<String, Component> createComponents() {
        AtomixValueComponent component = new AtomixValueComponent();
        component.setNodes(Collections.singletonList(getReplicaAddress()));

        return Collections.singletonMap("atomix-value", component);
    }

    @Override
    protected void doPreSetup() throws Exception {
        super.doPreSetup();

        value = getClient().getValue(VALUE_NAME).join();
    }

    @Override
    public void tearDown() throws Exception {
        value.close();

        super.tearDown();
    }

    // ************************************
    // Test
    // ************************************

    @Test
    public void testEvents() throws Exception {
        String val1 = context().getUuidGenerator().generateUuid();
        String val2 = context().getUuidGenerator().generateUuid();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);

        mock.message(0).body().isEqualTo(val1);
        mock.message(0).header(AtomixClientConstants.EVENT_TYPE).isEqualTo(DistributedValue.Events.CHANGE);
        mock.message(1).body().isEqualTo(val2);
        mock.message(1).header(AtomixClientConstants.EVENT_TYPE).isEqualTo(DistributedValue.Events.CHANGE);

        value.set(val1).join();
        value.compareAndSet(val1, val2).join();

        mock.assertIsSatisfied();
    }

    // ************************************
    // Routes
    // ************************************

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                fromF("atomix-value:%s", VALUE_NAME)
                    .to("mock:result");
            }
        };
    }
}
