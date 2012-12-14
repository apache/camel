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
package org.apache.camel.component.redis;

import java.util.Collection;
import java.util.Iterator;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.Topic;

import static org.hamcrest.CoreMatchers.is;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class RedisConsumerTest extends CamelTestSupport {
    private RedisMessageListenerContainer listenerContainer;

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();
        registry.bind("listenerContainer", listenerContainer);
        return registry;
    }

    @Before
    public void setUp() throws Exception {
        listenerContainer = mock(RedisMessageListenerContainer.class);
        super.setUp();
    }

    @Test
    public void registerConsumerForTwoChannelTopics() throws Exception {
        ArgumentCaptor<Collection> collectionCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(listenerContainer).addMessageListener(any(MessageListener.class), collectionCaptor.capture());

        Collection<ChannelTopic> topics = collectionCaptor.getValue();
        Iterator<ChannelTopic> topicIterator = topics.iterator();

        Topic firstTopic = topicIterator.next();
        Topic twoTopic = topicIterator.next();
        assertThat(firstTopic.getTopic(), is("one"));
        assertThat(twoTopic.getTopic(), is("two"));
    }

    @Test
    public void consumerReceivesMessages() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(2);

        ArgumentCaptor<MessageListener> messageListenerCaptor = ArgumentCaptor
                .forClass(MessageListener.class);
        verify(listenerContainer).addMessageListener(messageListenerCaptor.capture(), any(Collection.class));

        MessageListener messageListener = messageListenerCaptor.getValue();
        messageListener.onMessage(new DefaultMessage(null, null), null);
        messageListener.onMessage(new DefaultMessage(null, null), null);

        mock.assertIsSatisfied();
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("redis://localhost:6379?command=SUBSCRIBE&channels=one,two&listenerContainer=#listenerContainer")
                        .to("mock:result");
            }
        };
    }
}
