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
package org.apache.camel.component.hazelcast;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.hazelcast.collection.IQueue;
import com.hazelcast.core.HazelcastInstance;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.hazelcast.seda.HazelcastSedaComponent;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The SEDA consumer must keep polling after a poll throws an Error (e.g. NoClassDefFoundError from deserialization),
 * not just after an Exception.
 */
class HazelcastSedaConsumerPollErrorThrowableTest extends CamelTestSupport {

    private final LinkedBlockingQueue<String> items = new LinkedBlockingQueue<>();
    private final AtomicBoolean failed = new AtomicBoolean();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        IQueue<String> queue = mock(IQueue.class);
        try {
            when(queue.poll(anyLong(), any(TimeUnit.class))).thenAnswer(invocation -> {
                // the first poll throws an Error simulating a deserialization failure
                if (failed.compareAndSet(false, true)) {
                    throw new NoClassDefFoundError("Simulated deserialization failure");
                }
                return items.poll(invocation.getArgument(0), invocation.getArgument(1));
            });
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        HazelcastInstance hazelcastInstance = mock(HazelcastInstance.class);
        when(hazelcastInstance.<String> getQueue("foo")).thenReturn(queue);

        CamelContext context = super.createCamelContext();
        HazelcastSedaComponent seda = new HazelcastSedaComponent(context);
        seda.setHazelcastInstance(hazelcastInstance);
        context.addComponent("hazelcast-seda", seda);
        return context;
    }

    @Test
    void testKeepsPollingAfterError() throws Exception {
        MockEndpoint out = getMockEndpoint("mock:result");
        out.expectedBodiesReceived("bar");

        items.add("bar");

        MockEndpoint.assertIsSatisfied(context, 5, TimeUnit.SECONDS);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(String.format("hazelcast-%sfoo?onErrorDelay=100", HazelcastConstants.SEDA_PREFIX))
                        .to("mock:result");
            }
        };
    }
}
