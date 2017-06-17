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
package org.apache.camel.component.atomix.client.map;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import io.atomix.collections.DistributedMap;
import org.apache.camel.Component;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.atomix.client.AtomixClientConstants;
import org.apache.camel.component.atomix.client.AtomixClientTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class AtomixMapConsumerTest extends AtomixClientTestSupport {
    private static final String MAP_NAME = UUID.randomUUID().toString();
    private static final String KEY_NAME = UUID.randomUUID().toString();
    private DistributedMap<Object, Object> map;

    // ************************************
    // Setup
    // ************************************

    @Override
    protected Map<String, Component> createComponents() {
        AtomixMapComponent component = new AtomixMapComponent();
        component.setNodes(Collections.singletonList(getReplicaAddress()));

        return Collections.singletonMap("atomix-map", component);
    }

    @Override
    protected void doPreSetup() throws Exception {
        super.doPreSetup();

        map = getClient().getMap(MAP_NAME).join();
    }

    @Override
    public void tearDown() throws Exception {
        map.close();

        super.tearDown();
    }

    // ************************************
    // Test
    // ************************************

    @Test
    public void testEvents() throws Exception {
        String key = context().getUuidGenerator().generateUuid();
        String put = context().getUuidGenerator().generateUuid();
        String upd = context().getUuidGenerator().generateUuid();

        MockEndpoint mock1 = getMockEndpoint("mock:result");
        mock1.expectedMessageCount(6);
        MockEndpoint mock2 = getMockEndpoint("mock:result-key");
        mock2.expectedMessageCount(2);

        mock1.message(0).body().isEqualTo(put);
        mock1.message(0).header(AtomixClientConstants.EVENT_TYPE).isEqualTo(DistributedMap.Events.ADD);
        mock1.message(0).header(AtomixClientConstants.RESOURCE_KEY).isEqualTo(key);

        mock1.message(1).body().isEqualTo(put);
        mock1.message(1).header(AtomixClientConstants.EVENT_TYPE).isEqualTo(DistributedMap.Events.UPDATE);
        mock1.message(1).header(AtomixClientConstants.RESOURCE_KEY).isEqualTo(key);

        mock1.message(2).body().isEqualTo(upd);
        mock1.message(2).header(AtomixClientConstants.EVENT_TYPE).isEqualTo(DistributedMap.Events.UPDATE);
        mock1.message(2).header(AtomixClientConstants.RESOURCE_KEY).isEqualTo(key);

        mock1.message(3).body().isEqualTo(upd);
        mock1.message(3).header(AtomixClientConstants.EVENT_TYPE).isEqualTo(DistributedMap.Events.REMOVE);
        mock1.message(3).header(AtomixClientConstants.RESOURCE_KEY).isEqualTo(key);

        mock1.message(4).body().isEqualTo(put);
        mock1.message(4).header(AtomixClientConstants.EVENT_TYPE).isEqualTo(DistributedMap.Events.ADD);
        mock1.message(4).header(AtomixClientConstants.RESOURCE_KEY).isEqualTo(KEY_NAME);

        mock1.message(5).body().isEqualTo(put);
        mock1.message(5).header(AtomixClientConstants.EVENT_TYPE).isEqualTo(DistributedMap.Events.REMOVE);
        mock1.message(5).header(AtomixClientConstants.RESOURCE_KEY).isEqualTo(KEY_NAME);

        mock2.message(0).body().isEqualTo(put);
        mock2.message(0).header(AtomixClientConstants.EVENT_TYPE).isEqualTo(DistributedMap.Events.ADD);
        mock2.message(0).header(AtomixClientConstants.RESOURCE_KEY).isEqualTo(KEY_NAME);

        mock2.message(1).body().isEqualTo(put);
        mock2.message(1).header(AtomixClientConstants.EVENT_TYPE).isEqualTo(DistributedMap.Events.REMOVE);
        mock2.message(1).header(AtomixClientConstants.RESOURCE_KEY).isEqualTo(KEY_NAME);

        map.put(key, put).join();
        map.put(key, put).join();
        map.replace(key, upd).join();
        map.remove(key).join();
        map.put(KEY_NAME, put).join();
        map.remove(KEY_NAME).join();

        mock1.assertIsSatisfied();
        mock2.assertIsSatisfied();
    }

    // ************************************
    // Routes
    // ************************************

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                fromF("atomix-map:%s", MAP_NAME)
                    .to("mock:result");
                fromF("atomix-map:%s?key=%s", MAP_NAME, KEY_NAME)
                    .to("mock:result-key");
            }
        };
    }
}
