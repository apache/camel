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

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IQueue;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;
import org.mockito.Mock;

import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class HazelcastQueueConsumerPollTest extends HazelcastCamelTestSupport {

    @Mock
    private IQueue<String> queue;
    
    @Override
    protected void trainHazelcastInstance(HazelcastInstance hazelcastInstance) {
        when(hazelcastInstance.<String>getQueue("foo")).thenReturn(queue);
    }

    @Override
    protected void verifyHazelcastInstance(HazelcastInstance hazelcastInstance) {
        verify(hazelcastInstance).getQueue("foo");
        try {
            verify(queue, atLeast(1)).poll(10000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void add() throws InterruptedException {
        when(queue.poll(10000, TimeUnit.MILLISECONDS)).thenReturn("foo");
        
        MockEndpoint out = getMockEndpoint("mock:result");
        out.expectedMessageCount(1);


        assertMockEndpointsSatisfied(2000, TimeUnit.MILLISECONDS);

        this.checkHeadersAbsence(out.getExchanges().get(0).getIn().getHeaders(), HazelcastConstants.ADDED);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(String.format("hazelcast-%sfoo?queueConsumerMode=Poll", HazelcastConstants.QUEUE_PREFIX)).to("mock:result");
            }
        };
    }

    private void checkHeadersAbsence(Map<String, Object> headers, String action) {
        assertNotEquals(action, headers.get(HazelcastConstants.LISTENER_ACTION));
        assertNotEquals(HazelcastConstants.CACHE_LISTENER, headers.get(HazelcastConstants.LISTENER_TYPE));
        assertNull(headers.get(HazelcastConstants.LISTENER_TIME));
    }

}
