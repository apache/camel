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

import java.nio.charset.StandardCharsets;
import java.util.Collection;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.Topic;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@MockitoSettings
public class RedisConsumerMessageTest extends CamelTestSupport {

    @Mock
    @BindToRegistry("listenerContainer")
    private RedisMessageListenerContainer listenerContainer;

    @Mock
    @BindToRegistry("myExceptionHandler")
    private ExceptionHandler exceptionHandler;

    @Captor
    private ArgumentCaptor<MessageListener> listenerCaptor;
    @Captor
    private ArgumentCaptor<Collection<Topic>> topicsCaptor;

    @Test
    public void shouldDeserializeJavaSerializedMessage() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello");

        byte[] body = new JdkSerializationRedisSerializer().serialize("Hello");
        listener("good").onMessage(new DefaultMessage("good".getBytes(StandardCharsets.UTF_8), body), null);

        mock.assertIsSatisfied();
    }

    @Test
    public void shouldGiveRawBytesWhenMessageCannotBeDeserialized() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived(RedisConstants.CHANNEL, "good");

        // such as published by redis-cli PUBLISH good "plain text"
        byte[] body = "plain text".getBytes(StandardCharsets.UTF_8);
        listener("good").onMessage(new DefaultMessage("good".getBytes(StandardCharsets.UTF_8), body), null);

        mock.assertIsSatisfied();
        assertArrayEquals(body, mock.getReceivedExchanges().get(0).getIn().getBody(byte[].class));
        verify(exceptionHandler, never()).handleException(anyString(), any(Exchange.class), any(Throwable.class));
    }

    @Test
    public void shouldHandFailedExchangeToExceptionHandler() {
        byte[] body = new JdkSerializationRedisSerializer().serialize("Hello");
        listener("bad").onMessage(new DefaultMessage("bad".getBytes(StandardCharsets.UTF_8), body), null);

        verify(exceptionHandler).handleException(anyString(), any(Exchange.class),
                argThat(e -> e instanceof IllegalStateException && "Forced".equals(e.getMessage())));
    }

    private MessageListener listener(String channel) {
        verify(listenerContainer, times(2))
                .addMessageListener(listenerCaptor.capture(), topicsCaptor.capture());
        for (int i = 0; i < topicsCaptor.getAllValues().size(); i++) {
            Topic topic = topicsCaptor.getAllValues().get(i).iterator().next();
            if (channel.equals(topic.getTopic())) {
                return listenerCaptor.getAllValues().get(i);
            }
        }
        throw new IllegalArgumentException("No consumer for channel " + channel);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("spring-redis://localhost:6379?command=SUBSCRIBE&channels=good&listenerContainer=#listenerContainer"
                     + "&exceptionHandler=#myExceptionHandler")
                        .to("mock:result");

                from("spring-redis://localhost:6379?command=SUBSCRIBE&channels=bad&listenerContainer=#listenerContainer"
                     + "&exceptionHandler=#myExceptionHandler")
                        .throwException(new IllegalStateException("Forced"));
            }
        };
    }
}
