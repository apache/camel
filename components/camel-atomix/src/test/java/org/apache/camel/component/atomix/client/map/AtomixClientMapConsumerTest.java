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
import java.util.UUID;

import io.atomix.collections.DistributedMap;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.atomix.client.AtomixClientConstants;
import org.apache.camel.component.atomix.client.AtomixClientTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;
import org.junit.Ignore;
import org.junit.Test;

@Ignore
public class AtomixClientMapConsumerTest extends AtomixClientTestSupport {
    private static final String MAP_NAME = UUID.randomUUID().toString();
    private DistributedMap<Object, Object> map;

    // ************************************
    // Setup
    // ************************************

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();

        AtomixClientMapComponent component = new AtomixClientMapComponent();
        component.setNodes(Collections.singletonList(getReplicaAddress()));

        registry.bind("atomix-map", component);

        return registry;
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
    public void test() throws Exception {
        String key = context().getUuidGenerator().generateUuid();
        String put = context().getUuidGenerator().generateUuid();
        String upd = context().getUuidGenerator().generateUuid();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.assertExchangeReceived(3);
        mock.expectedBodiesReceived(put, upd, upd);
        mock.expectedHeaderReceived(AtomixClientConstants.RESOURCE_KEY, key);

        map.put(key, put).join();
        map.replace(key, upd).join();
        map.remove(key).join();

        mock.assertIsSatisfied();

        assertEquals(
            DistributedMap.Events.ADD,
            mock.getExchanges().get(0).getIn().getHeader(AtomixClientConstants.EVENT_TYPE)
        );
        assertEquals(
            DistributedMap.Events.UPDATE,
            mock.getExchanges().get(1).getIn().getHeader(AtomixClientConstants.EVENT_TYPE)
        );
        assertEquals(
            DistributedMap.Events.REMOVE,
            mock.getExchanges().get(2).getIn().getHeader(AtomixClientConstants.EVENT_TYPE)
        );
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
            }
        };
    }
}
