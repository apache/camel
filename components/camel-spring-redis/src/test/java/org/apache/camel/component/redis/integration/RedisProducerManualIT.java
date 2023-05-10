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
package org.apache.camel.component.redis.integration;

import org.apache.camel.component.redis.RedisConstants;
import org.apache.camel.component.redis.RedisTestSupport;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.SimpleRegistry;
import org.apache.camel.test.infra.redis.services.RedisService;
import org.apache.camel.test.infra.redis.services.RedisServiceFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RedisProducerManualIT extends RedisTestSupport {

    @RegisterExtension
    static RedisService service = RedisServiceFactory.createService();
    private static JedisConnectionFactory connectionFactory;

    @BeforeAll
    public static void beforeAll() {
        connectionFactory = new JedisConnectionFactory();
        connectionFactory.getStandaloneConfiguration()
                .setHostName(service.host());
        connectionFactory.getStandaloneConfiguration()
                .setPort(service.port());

        connectionFactory.afterPropertiesSet();
    }

    @Override
    protected Registry createCamelRegistry() throws Exception {
        Registry registry = new SimpleRegistry();
        redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(connectionFactory);
        redisTemplate.afterPropertiesSet();

        registry.bind("redisTemplate", redisTemplate);
        return registry;
    }

    @Test
    public void shouldSetAString() throws Exception {
        sendHeaders(RedisConstants.COMMAND, "SET", RedisConstants.KEY, "key1", RedisConstants.VALUE, "value");

        assertEquals("value", redisTemplate.opsForValue().get("key1"));
    }

    @Test
    public void shouldGetAString() throws Exception {
        redisTemplate.opsForValue().set("key2", "value");
        Object result = sendHeaders(RedisConstants.KEY, "key2", RedisConstants.COMMAND, "GET");

        assertEquals("value", result);
    }
}
