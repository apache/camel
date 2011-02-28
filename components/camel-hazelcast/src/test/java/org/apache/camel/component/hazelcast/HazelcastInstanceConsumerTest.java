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
import com.hazelcast.core.HazelcastInstance;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Ignore;
import org.junit.Test;

public class HazelcastInstanceConsumerTest extends CamelTestSupport {

    @Test
    public void testAddInstance() throws InterruptedException {

        MockEndpoint added = getMockEndpoint("mock:added");
        added.setExpectedMessageCount(2);

        Hazelcast.newHazelcastInstance(null);
        Hazelcast.newHazelcastInstance(null);

        assertMockEndpointsSatisfied(5000, TimeUnit.MILLISECONDS);

        // check headers
        Exchange ex = added.getExchanges().get(0);
        Map<String, Object> headers = ex.getIn().getHeaders();

        this.checkHeaders(headers, HazelcastConstants.ADDED);

        Hazelcast.shutdownAll();
    }

    @Test
    @SuppressWarnings("deprecation")
    @Ignore("Shutdown causes further hazelast tests to fail")
    public void testRemoveInstance() throws InterruptedException {

        MockEndpoint removed = getMockEndpoint("mock:removed");
        removed.setExpectedMessageCount(1);

        HazelcastInstance h1 = Hazelcast.newHazelcastInstance(null);

        // TODO --> check how an instance can be killed...
        h1.shutdown();

        assertMockEndpointsSatisfied(5000, TimeUnit.MILLISECONDS);

        // check headers
        Exchange ex = removed.getExchanges().get(0);
        Map<String, Object> headers = ex.getIn().getHeaders();

        this.checkHeaders(headers, HazelcastConstants.REMOVED);

        Hazelcast.shutdownAll();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(String.format("hazelcast:%sfoo", HazelcastConstants.INSTANCE_PREFIX)).log("instance...").choice()
                        .when(header(HazelcastConstants.LISTENER_ACTION).isEqualTo(HazelcastConstants.ADDED)).log("...added").to("mock:added").otherwise().log("...removed").to("mock:removed");
            }
        };
    }

    private void checkHeaders(Map<String, Object> headers, String action) {
        assertEquals(action, headers.get(HazelcastConstants.LISTENER_ACTION));
        assertEquals(HazelcastConstants.INSTANCE_LISTENER, headers.get(HazelcastConstants.LISTENER_TYPE));
        assertNotNull(headers.get(HazelcastConstants.LISTENER_TIME));
        assertNotNull(headers.get(HazelcastConstants.INSTANCE_HOST));
        assertNotNull(headers.get(HazelcastConstants.INSTANCE_PORT));
    }
}
