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
package org.apache.camel.component.redis;

import java.util.Collection;
import java.util.Iterator;

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.Topic;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class RedisConsumerTest extends CamelTestSupport {

    @Mock
    @BindToRegistry("listenerContainer")
    private RedisMessageListenerContainer listenerContainer;

    @Captor
    private ArgumentCaptor<Collection<ChannelTopic>> collectionCaptor;
    @Captor
    private ArgumentCaptor<MessageListener> messageListenerCaptor;

    @Test
    public void registerConsumerForTwoChannelTopics() throws Exception {
        verify(listenerContainer).addMessageListener(any(MessageListener.class), collectionCaptor.capture());

        Collection<ChannelTopic> topics = collectionCaptor.getValue();
        Iterator<ChannelTopic> topicIterator = topics.iterator();

        Topic firstTopic = topicIterator.next();
        Topic twoTopic = topicIterator.next();

        assertEquals("one", firstTopic.getTopic());
        assertEquals("two", twoTopic.getTopic());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("spring-redis://localhost:6379?command=SUBSCRIBE&channels=one,two&listenerContainer=#listenerContainer")
                        .to("mock:result");
            }
        };
    }
}
