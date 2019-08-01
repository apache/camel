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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.SimpleRegistry;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Ignore
public class RedisConsumerIntegrationTest extends RedisTestSupport {
    private static final JedisConnectionFactory CONNECTION_FACTORY = new JedisConnectionFactory();
    private static final RedisMessageListenerContainer LISTENER_CONTAINER = new RedisMessageListenerContainer();

    static {
        CONNECTION_FACTORY.afterPropertiesSet();
        LISTENER_CONTAINER.setConnectionFactory(CONNECTION_FACTORY);
        LISTENER_CONTAINER.afterPropertiesSet();
    }

    @Override
    protected Registry createCamelRegistry() throws Exception {
        Registry registry = new SimpleRegistry();

        redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(CONNECTION_FACTORY);
        redisTemplate.afterPropertiesSet();

        registry.bind("redisTemplate", redisTemplate);
        registry.bind("listenerContainer", LISTENER_CONTAINER);
        return registry;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("spring-redis://localhost:6379?command=SUBSCRIBE&channels=one,two&listenerContainer=#listenerContainer&redisTemplate=#redisTemplate")
                        .startupOrder(1)
                        .to("mock:result");

                from("direct:start")
                        .startupOrder(2)
                        .delay(2000)
                        .to("spring-redis://localhost:6379?redisTemplate=#redisTemplate");
            }
        };
    }

    @Test
    public void consumerReceivesMessages() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        mock.expectedBodiesReceived("message");

        sendHeaders(
                       RedisConstants.COMMAND, "PUBLISH",
                       RedisConstants.CHANNEL, "two",
                       RedisConstants.MESSAGE, "message");
        mock.assertIsSatisfied();
    }
}

