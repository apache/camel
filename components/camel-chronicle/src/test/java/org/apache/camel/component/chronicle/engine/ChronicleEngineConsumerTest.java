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

package org.apache.camel.component.chronicle.engine;

import java.util.Map;
import java.util.UUID;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class ChronicleEngineConsumerTest extends ChronicleEngineTestSupport {

    @Test
    public void testMapEvents() throws Exception {
        final String key = UUID.randomUUID().toString();

        MockEndpoint mock = getMockEndpoint("mock:map-events");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("val");
        mock.expectedHeaderReceived(ChronicleEngineConstants.PATH, "/my/path");
        mock.expectedHeaderReceived(ChronicleEngineConstants.KEY, key);

        Map<String, String> map = client().acquireMap("/my/path", String.class, String.class);
        map.put(key, "val");

        mock.assertIsSatisfied();
    }

    @Test
    public void testMapEventsFiltering() throws Exception {
        final String key = UUID.randomUUID().toString();

        MockEndpoint mock = getMockEndpoint("mock:map-events-filtering");
        mock.expectedMessageCount(2);

        Map<String, String> map = client().acquireMap("/my/path", String.class, String.class);
        map.put(key, "val-1");
        map.put(key, "val-2");
        map.remove(key);

        mock.assertIsSatisfied();

        assertEquals(
            ChronicleEngineMapEventType.INSERT,
            mock.getExchanges().get(0).getIn().getHeader(ChronicleEngineConstants.MAP_EVENT_TYPE));
        assertEquals(
            "val-1",
            mock.getExchanges().get(0).getIn().getBody(String.class));
        assertEquals(
            ChronicleEngineMapEventType.REMOVE,
            mock.getExchanges().get(1).getIn().getHeader(ChronicleEngineConstants.MAP_EVENT_TYPE));
        assertEquals(
            "val-2",
            mock.getExchanges().get(1).getIn().getHeader(ChronicleEngineConstants.OLD_VALUE));
    }

    @Test
    public void testTopologicalEvents() throws Exception {
        final String key = UUID.randomUUID().toString();

        MockEndpoint mock = getMockEndpoint("mock:topological-events");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived((String)null);
        mock.expectedHeaderReceived(ChronicleEngineConstants.PATH, "/my/path");
        mock.expectedHeaderReceived(ChronicleEngineConstants.TOPOLOGICAL_EVENT_FULL_NAME, "/my/path");

        Map<String, String> map = client().acquireMap("/my/path", String.class, String.class);
        map.put(key, "val");

        mock.assertIsSatisfied();
    }

    @Test
    public void testTopicEvents() throws Exception {
        final String key = UUID.randomUUID().toString();

        MockEndpoint mock = getMockEndpoint("mock:topic-events");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("val");
        mock.expectedHeaderReceived(ChronicleEngineConstants.PATH, "/my/path");

        Map<String, String> map = client().acquireMap("/my/path", String.class, String.class);
        map.put(key, "val");

        mock.assertIsSatisfied();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                fromF("chronicle-engine://%s/my/path?persistent=false", getAddress())
                    .to("mock:map-events");
                fromF("chronicle-engine://%s/my/path?persistent=false&filteredMapEvents=update", getAddress())
                    .to("mock:map-events-filtering");
                fromF("chronicle-engine://%s/my/path?persistent=false&subscribeMapEvents=false&subscribeTopologicalEvents=true", getAddress())
                    .to("mock:topological-events");
                fromF("chronicle-engine://%s/my/path?persistent=false&subscribeMapEvents=false&subscribeTopicEvents=true", getAddress())
                    //.to("log:org.apache.camel.component.chronicle.engine?level=INFO&showAll=true")
                    .to("mock:topic-events");
            }
        };
    }
}
