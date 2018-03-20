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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.impl.JndiRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class RedisHashTest extends RedisTestSupport {

    @Mock
    private RedisTemplate<String, String> redisTemplate;
    @Mock
    private HashOperations<String, String, String> hashOperations;

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        when(redisTemplate.<String, String>opsForHash()).thenReturn(hashOperations);

        JndiRegistry registry = super.createRegistry();
        registry.bind("redisTemplate", redisTemplate);
        return registry;
    }

    @Test
    public void shouldExecuteHDEL() throws Exception {
        sendHeaders(
                RedisConstants.COMMAND, "HDEL",
                RedisConstants.KEY, "key",
                RedisConstants.FIELD, "field");

        verify(hashOperations).delete("key", "field");
    }

    @Test
    public void shouldExecuteHEXISTS() throws Exception {
        when(hashOperations.hasKey(anyString(), anyString())).thenReturn(true);

        Object result = sendHeaders(
                RedisConstants.COMMAND, "HEXISTS",
                RedisConstants.KEY, "key",
                RedisConstants.FIELD, "field");

        verify(hashOperations).hasKey("key", "field");
        assertEquals(true, result);
    }

    @Test
    public void shouldExecuteHINCRBY() throws Exception {
        when(hashOperations.increment(anyString(), anyString(), anyLong())).thenReturn(1L);

        Object result = sendHeaders(
                RedisConstants.COMMAND, "HINCRBY",
                RedisConstants.KEY, "key",
                RedisConstants.FIELD, "field",
                RedisConstants.VALUE, "1");

        verify(hashOperations).increment("key", "field", 1L);
        assertEquals(1L, result);
    }

    @Test
    public void shouldExecuteHKEYS() throws Exception {
        Set<String> fields = new HashSet<>(Arrays.asList(new String[]{"field1, field2"}));
        when(hashOperations.keys(anyString())).thenReturn(fields);

        Object result = sendHeaders(
                RedisConstants.COMMAND, "HKEYS",
                RedisConstants.KEY, "key");

        verify(hashOperations).keys("key");
        assertEquals(fields, result);
    }


    @Test
    public void shouldExecuteHMSET() throws Exception {
        Map<String, String> values = new HashMap<>();
        values.put("field1", "value1");
        values.put("field2", "value");

        sendHeaders(
                RedisConstants.COMMAND, "HMSET",
                RedisConstants.KEY, "key",
                RedisConstants.VALUES, values);

        verify(hashOperations).putAll("key", values);
    }

    @Test
    public void shouldExecuteHVALS() throws Exception {
        List<String> values = new ArrayList<>();
        values.add("val1");
        values.add("val2");

        when(hashOperations.values(anyString())).thenReturn(values);

        Object result = sendHeaders(
                RedisConstants.COMMAND, "HVALS",
                RedisConstants.KEY, "key",
                RedisConstants.VALUES, values);

        verify(hashOperations).values("key");
        assertEquals(values, result);
    }

    @Test
    public void shouldExecuteHLEN() throws Exception {
        when(hashOperations.size(anyString())).thenReturn(2L);

        Object result = sendHeaders(
                RedisConstants.COMMAND, "HLEN",
                RedisConstants.KEY, "key");

        verify(hashOperations).size("key");
        assertEquals(2L, result);
    }

    @Test
    public void shouldSetHashValue() throws Exception {
        sendHeaders(
                RedisConstants.COMMAND, "HSET",
                RedisConstants.KEY, "key",
                RedisConstants.FIELD, "field",
                RedisConstants.VALUE, "value");

        verify(hashOperations).put("key", "field", "value");
    }

    @Test
    public void shouldExecuteHSETNX() throws Exception {
        when(hashOperations.putIfAbsent(anyString(), anyString(), anyString())).thenReturn(true);

        Object result = sendHeaders(
                RedisConstants.COMMAND, "HSETNX",
                RedisConstants.KEY, "key",
                RedisConstants.FIELD, "field",
                RedisConstants.VALUE, "value");

        verify(hashOperations).putIfAbsent("key", "field", "value");
        assertEquals(true, result);
    }


    @Test
    public void shouldExecuteHGET() throws Exception {
        when(hashOperations.get(anyString(), anyString())).thenReturn("value");

        Object result = sendHeaders(
                RedisConstants.COMMAND, "HGET",
                RedisConstants.KEY, "key",
                RedisConstants.FIELD, "field");

        verify(hashOperations).get("key", "field");
        assertEquals("value", result);
    }

    @Test
    public void shouldExecuteHGETALL() throws Exception {
        HashMap<String, String> values = new HashMap<>();
        values.put("field1", "valu1");
        when(hashOperations.entries(anyString())).thenReturn(values);

        Object result = sendHeaders(
                RedisConstants.COMMAND, "HGETALL",
                RedisConstants.KEY, "key");

        verify(hashOperations).entries("key");
        assertEquals(values, result);
    }

    @Test
    public void shouldExecuteHMGET() throws Exception {
        List<String> fields = new ArrayList<>();
        fields.add("field1");
        when(hashOperations.multiGet(anyString(), anyList())).thenReturn(fields);

        Object result = sendHeaders(
                RedisConstants.COMMAND, "HMGET",
                RedisConstants.KEY, "key",
                RedisConstants.FIELDS, fields);

        verify(hashOperations).multiGet("key", fields);
        assertEquals(fields, result);
    }
}
