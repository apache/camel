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

import org.apache.camel.Endpoint;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.SimpleRegistry;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.data.redis.core.RedisTemplate;

public class RedisRemoveEndpointTest extends CamelTestSupport {

    @Override
    protected Registry createCamelRegistry() throws Exception {
        Registry registry = new SimpleRegistry();
        redisTemplate = new RedisTemplate<>();
        registry.bind("redisTemplate", redisTemplate);
        return registry;
    }

    @Mock
    protected RedisTemplate<String, String> redisTemplate;

    @Test
    public void testRemoveEndpointWithHashtag() throws Exception {
        Endpoint e = context.getEndpoint("spring-redis://localhost:6379?redisTemplate=#redisTemplate");
        context.getEndpoint("direct:someotherendpoint");

        Assertions.assertEquals(2, context.getEndpoints().size());
        context.removeEndpoint(e);
        Assertions.assertEquals(1, context.getEndpoints().size());
    }
}
