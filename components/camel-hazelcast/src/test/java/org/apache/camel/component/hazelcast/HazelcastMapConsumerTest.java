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
package org.apache.camel.component.hazelcast;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.IMap;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class HazelcastMapConsumerTest extends CamelTestSupport {

    private IMap<String, Object> map;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        this.map = Hazelcast.getMap("foo");
        this.map.clear();
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();

        this.map.clear();
    }

    @Test
    public void testAdd() throws InterruptedException {
        MockEndpoint out = getMockEndpoint("mock:added");
        out.expectedMessageCount(1);

        map.put("4711", "my-foo");

        assertMockEndpointsSatisfied(5000, TimeUnit.MILLISECONDS);

        this.checkHeaders(out.getExchanges().get(0).getIn().getHeaders(), HazelcastConstants.ADDED);
    }

    @Test
    public void testEnvict() throws InterruptedException {
        MockEndpoint out = super.getMockEndpoint("mock:envicted");
        out.expectedMessageCount(5);

        map.put("1", "my-foo-1");
        map.put("2", "my-foo-2");
        map.put("3", "my-foo-3");
        map.put("4", "my-foo-4");
        map.put("5", "my-foo-5");
        map.put("6", "my-foo-6");

        assertMockEndpointsSatisfied(30000, TimeUnit.MILLISECONDS);
    }

    @Test
    public void testUpdate() throws InterruptedException {
        MockEndpoint out = getMockEndpoint("mock:updated");
        out.expectedMessageCount(1);

        map.put("4711", "my-foo");
        map.replace("4711", "my-fooo");

        assertMockEndpointsSatisfied(5000, TimeUnit.MILLISECONDS);

        this.checkHeaders(out.getExchanges().get(0).getIn().getHeaders(), HazelcastConstants.UPDATED);
    }

    @Test
    public void testRemove() throws InterruptedException {
        MockEndpoint out = getMockEndpoint("mock:removed");
        out.expectedMessageCount(1);

        map.put("4711", "my-foo");
        map.remove("4711");

        assertMockEndpointsSatisfied(5000, TimeUnit.MILLISECONDS);
        this.checkHeaders(out.getExchanges().get(0).getIn().getHeaders(), HazelcastConstants.REMOVED);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(String.format("hazelcast:%sfoo", HazelcastConstants.MAP_PREFIX)).log("object...").choice().when(header(HazelcastConstants.LISTENER_ACTION).isEqualTo(HazelcastConstants.ADDED))
                        .log("...added").to("mock:added").when(header(HazelcastConstants.LISTENER_ACTION).isEqualTo(HazelcastConstants.ENVICTED)).log("...envicted").to("mock:envicted")
                        .when(header(HazelcastConstants.LISTENER_ACTION).isEqualTo(HazelcastConstants.UPDATED)).log("...updated").to("mock:updated")
                        .when(header(HazelcastConstants.LISTENER_ACTION).isEqualTo(HazelcastConstants.REMOVED)).log("...removed").to("mock:removed").otherwise().log("fail!");

            }
        };
    }

    private void checkHeaders(Map<String, Object> headers, String action) {
        assertEquals(action, headers.get(HazelcastConstants.LISTENER_ACTION));
        assertEquals(HazelcastConstants.CACHE_LISTENER, headers.get(HazelcastConstants.LISTENER_TYPE));
        assertEquals("4711", headers.get(HazelcastConstants.OBJECT_ID));
        assertNotNull(headers.get(HazelcastConstants.LISTENER_TIME));
    }
}
