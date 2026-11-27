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

import java.util.Map;

import org.apache.camel.spi.EndpointUriFactory;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RedisEndpointUriFactoryTest extends CamelTestSupport {

    @Test
    public void shouldUseDefaultHostAndPortWhenBuildingUri() throws Exception {
        EndpointUriFactory factory = context.getCamelContextExtension().getEndpointUriFactory("spring-redis");

        assertEquals("spring-redis:localhost:6379?command=GET",
                factory.buildUri("spring-redis", Map.of("command", "GET"), false));
        assertEquals("spring-redis:localhost:6380", factory.buildUri("spring-redis", Map.of("port", 6380), false));
        assertEquals("spring-redis:redis.example.com:6379",
                factory.buildUri("spring-redis", Map.of("host", "redis.example.com"), false));
    }
}
