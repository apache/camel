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

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.impl.JndiRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.data.redis.core.RedisTemplate;

import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class RedisTransactionTest extends RedisTestSupport {

    @Mock
    private RedisTemplate<String, ?> redisTemplate;

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();
        registry.bind("redisTemplate", redisTemplate);
        return registry;
    }

    @Test
    public void shouldExecuteMULTI() throws Exception {
        sendHeaders(RedisConstants.COMMAND, "MULTI");
        verify(redisTemplate).multi();
    }

    @Test
    public void shouldExecuteDISCARD() throws Exception {
        sendHeaders(RedisConstants.COMMAND, "DISCARD");
        verify(redisTemplate).discard();
    }

    @Test
    public void shouldExecuteEXEC() throws Exception {
        sendHeaders(RedisConstants.COMMAND, "EXEC");
        verify(redisTemplate).exec();
    }

    @Test
    public void shouldExecuteUNWATCH() throws Exception {
        sendHeaders(RedisConstants.COMMAND, "UNWATCH");
        verify(redisTemplate).unwatch();
    }

    @Test
    public void shouldExecuteWATCH() throws Exception {
        List<String> keys = new ArrayList<>();
        keys.add("key");

        sendHeaders(
                RedisConstants.COMMAND, "WATCH",
                RedisConstants.KEYS, keys);
        verify(redisTemplate).watch(keys);
    }

}

