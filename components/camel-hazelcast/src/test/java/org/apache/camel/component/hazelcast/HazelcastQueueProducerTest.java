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

import java.util.Queue;
import java.util.concurrent.BlockingQueue;

import com.hazelcast.core.Hazelcast;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class HazelcastQueueProducerTest extends CamelTestSupport {

    @Test
    public void put() throws InterruptedException {
        Queue<Object> queue = Hazelcast.getQueue("bar");
        queue.clear();

        template.sendBody("direct:put", "foo");

        assertTrue(queue.contains("foo"));

        queue.clear();
    }

    @Test
    public void add() throws InterruptedException {
        Queue<Object> queue = Hazelcast.getQueue("bar");
        queue.clear();

        template.sendBody("direct:add", "bar");

        assertTrue(queue.contains("bar"));

        queue.clear();
    }

    @Test
    public void offer() throws InterruptedException {
        Queue<Object> queue = Hazelcast.getQueue("bar");
        queue.clear();

        template.sendBody("direct:offer", "foobar");
        assertTrue(queue.contains("foobar"));

        queue.clear();
    }

    @Test
    public void removeValue() throws InterruptedException {
        BlockingQueue<String> queue = Hazelcast.getQueue("bar");
        queue.clear();

        queue.put("foo1");
        queue.put("foo2");
        queue.put("foo3");

        assertEquals(3, queue.size());

        // specify the value to remove
        template.sendBody("direct:removevalue", "foo2");
        assertEquals(2, queue.size());
        assertTrue(queue.contains("foo1") && queue.contains("foo3"));

        // do not specify the value to delete (null)
        template.sendBody("direct:removevalue", null);
        assertEquals(1, queue.size());

        assertTrue(queue.contains("foo3"));

        queue.clear();
    }

    @Test
    public void poll() throws InterruptedException {
        BlockingQueue<String> queue = Hazelcast.getQueue("bar");
        queue.clear();

        queue.put("foo");
        assertEquals(1, queue.size());

        template.sendBody("direct:poll", null);

        assertFalse(queue.contains("foo"));
        assertEquals(0, queue.size());

        queue.clear();
    }

    @Test
    public void peek() throws InterruptedException {
        BlockingQueue<String> queue = Hazelcast.getQueue("bar");
        queue.clear();

        queue.put("foo");
        assertEquals(1, queue.size());

        template.sendBody("direct:peek", null);

        assertEquals(1, queue.size());
        assertTrue(queue.contains("foo"));

        queue.clear();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                from("direct:put").setHeader(HazelcastConstants.OPERATION, constant(HazelcastConstants.PUT_OPERATION)).to(String.format("hazelcast:%sbar", HazelcastConstants.QUEUE_PREFIX));

                from("direct:add").setHeader(HazelcastConstants.OPERATION, constant(HazelcastConstants.ADD_OPERATION)).to(String.format("hazelcast:%sbar", HazelcastConstants.QUEUE_PREFIX));

                from("direct:offer").setHeader(HazelcastConstants.OPERATION, constant(HazelcastConstants.OFFER_OPERATION)).to(String.format("hazelcast:%sbar", HazelcastConstants.QUEUE_PREFIX));

                from("direct:poll").setHeader(HazelcastConstants.OPERATION, constant(HazelcastConstants.POLL_OPERATION)).to(String.format("hazelcast:%sbar", HazelcastConstants.QUEUE_PREFIX));

                from("direct:peek").setHeader(HazelcastConstants.OPERATION, constant(HazelcastConstants.PEEK_OPERATION)).to(String.format("hazelcast:%sbar", HazelcastConstants.QUEUE_PREFIX));

                from("direct:removevalue").setHeader(HazelcastConstants.OPERATION, constant(HazelcastConstants.REMOVEVALUE_OPERATION)).to(
                        String.format("hazelcast:%sbar", HazelcastConstants.QUEUE_PREFIX));

            }
        };
    }

}
