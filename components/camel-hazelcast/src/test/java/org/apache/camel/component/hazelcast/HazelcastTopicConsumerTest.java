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
import com.hazelcast.core.ITopic;
import com.hazelcast.core.Message;
import com.hazelcast.core.MessageListener;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class HazelcastTopicConsumerTest extends HazelcastCamelTestSupport {

    @Mock
    private ITopic<String> topic;

    @Captor
    private ArgumentCaptor<MessageListener<String>> argument;

    @Override
    protected void trainHazelcastInstance(HazelcastInstance hazelcastInstance) {
        when(hazelcastInstance.<String>getTopic("foo")).thenReturn(topic);
        when(topic.addMessageListener(any())).thenReturn("foo");
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void verifyHazelcastInstance(HazelcastInstance hazelcastInstance) {
        verify(hazelcastInstance).getTopic("foo");
        verify(topic).addMessageListener(any(MessageListener.class));
    }

    @Test
    public void receive() throws InterruptedException {
        MockEndpoint out = getMockEndpoint("mock:received");
        out.expectedMessageCount(1);

        verify(topic).addMessageListener(argument.capture());
        final Message<String> msg = new Message<String>("foo", "foo", new java.util.Date().getTime(), null);
        argument.getValue().onMessage(msg);

        assertMockEndpointsSatisfied(2000, TimeUnit.MILLISECONDS);

        this.checkHeaders(out.getExchanges().get(0).getIn().getHeaders(), HazelcastConstants.RECEIVED);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(String.format("hazelcast-%sfoo", HazelcastConstants.TOPIC_PREFIX)).log("object...")
                        .choice()
                            .when(header(HazelcastConstants.LISTENER_ACTION).isEqualTo(HazelcastConstants.RECEIVED))
                                .log("...received").to("mock:received")
                        .otherwise()
                            .log("fail!");
            }
        };
    }

    private void checkHeaders(Map<String, Object> headers, String action) {
        assertEquals(action, headers.get(HazelcastConstants.LISTENER_ACTION));
        assertEquals(HazelcastConstants.CACHE_LISTENER, headers.get(HazelcastConstants.LISTENER_TYPE));
        assertEquals(null, headers.get(HazelcastConstants.OBJECT_ID));
        assertNotNull(headers.get(HazelcastConstants.LISTENER_TIME));
    }

}
