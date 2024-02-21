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

import java.util.HashSet;
import java.util.Set;

import org.apache.camel.spi.Registry;
import org.apache.camel.support.SimpleRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@MockitoSettings
public class RedisSetTest extends RedisTestSupport {

    @Mock
    private RedisTemplate<String, String> redisTemplate;
    @Mock
    private SetOperations<String, String> setOperations;

    @Override
    protected Registry createCamelRegistry() throws Exception {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);

        Registry registry = new SimpleRegistry();
        registry.bind("redisTemplate", redisTemplate);
        return registry;
    }

    @Test
    public void shouldExecuteSADD() throws Exception {
        when(setOperations.add(anyString(), any())).thenReturn(null);

        Object result = sendHeaders(
                RedisConstants.COMMAND, "SADD",
                RedisConstants.KEY, "key",
                RedisConstants.VALUE, "value");

        verify(setOperations).add("key", "value");
        assertNull(result);

    }

    @Test
    public void shouldExecuteSCARD() throws Exception {
        when(setOperations.size(anyString())).thenReturn(2L);

        Object result = sendHeaders(
                RedisConstants.COMMAND, "SCARD",
                RedisConstants.KEY, "key");

        verify(setOperations).size("key");
        assertEquals(2L, result);
    }

    @Test
    public void shouldExecuteSDIFF() throws Exception {
        Set<String> difference = new HashSet<>();
        difference.add("a");
        difference.add("b");
        when(setOperations.difference(anyString(), anySet())).thenReturn(difference);

        Set<String> keys = new HashSet<>();
        keys.add("key2");
        keys.add("key3");
        Object result = sendHeaders(
                RedisConstants.COMMAND, "SDIFF",
                RedisConstants.KEY, "key",
                RedisConstants.KEYS, keys);

        verify(setOperations).difference("key", keys);
        assertEquals(difference, result);
    }

    @Test
    public void shouldExecuteSDIFFSTORE() throws Exception {
        Set<String> keys = new HashSet<>();
        keys.add("key2");
        keys.add("key3");
        sendHeaders(
                RedisConstants.COMMAND, "SDIFFSTORE",
                RedisConstants.KEY, "key",
                RedisConstants.KEYS, keys,
                RedisConstants.DESTINATION, "destination");

        verify(setOperations).differenceAndStore("key", keys, "destination");
    }

    @Test
    public void shouldExecuteSINTER() throws Exception {
        Set<String> difference = new HashSet<>();
        difference.add("a");
        difference.add("b");
        when(setOperations.intersect(anyString(), anySet())).thenReturn(difference);

        Set<String> keys = new HashSet<>();
        keys.add("key2");
        keys.add("key3");
        Object result = sendHeaders(
                RedisConstants.COMMAND, "SINTER",
                RedisConstants.KEY, "key",
                RedisConstants.KEYS, keys);

        verify(setOperations).intersect("key", keys);
        assertEquals(difference, result);
    }

    @Test
    public void shouldExecuteSINTERSTORE() throws Exception {
        Set<String> keys = new HashSet<>();
        keys.add("key2");
        keys.add("key3");
        sendHeaders(
                RedisConstants.COMMAND, "SINTERSTORE",
                RedisConstants.KEY, "key",
                RedisConstants.DESTINATION, "destination",
                RedisConstants.KEYS, keys);

        verify(setOperations).intersectAndStore("key", keys, "destination");
    }

    @Test
    public void shouldExecuteSMEMBERS() throws Exception {
        Set<String> keys = new HashSet<>();
        keys.add("key2");
        keys.add("key3");

        when(setOperations.members(anyString())).thenReturn(keys);

        Object result = sendHeaders(
                RedisConstants.COMMAND, "SMEMBERS",
                RedisConstants.KEY, "key");

        verify(setOperations).members("key");
        assertEquals(keys, result);
    }

    @Test
    public void shouldExecuteSMOVE() throws Exception {
        sendHeaders(
                RedisConstants.COMMAND, "SMOVE",
                RedisConstants.KEY, "key",
                RedisConstants.VALUE, "value",
                RedisConstants.DESTINATION, "destination");

        verify(setOperations).move("key", "value", "destination");
    }

    @Test
    public void shouldExecuteSPOP() throws Exception {
        String field = "value";
        when(setOperations.pop(anyString())).thenReturn(field);

        Object result = sendHeaders(
                RedisConstants.COMMAND, "SPOP",
                RedisConstants.KEY, "key");

        verify(setOperations).pop("key");
        assertEquals(field, result);
    }

    @Test
    public void shouldExecuteSRANDMEMBER() throws Exception {
        String field = "value";
        when(setOperations.randomMember(anyString())).thenReturn(field);

        Object result = sendHeaders(
                RedisConstants.COMMAND, "SRANDMEMBER",
                RedisConstants.KEY, "key");

        verify(setOperations).randomMember("key");
        assertEquals(field, result);
    }

    @Test
    public void shouldExecuteSREM() throws Exception {
        when(setOperations.remove(anyString(), any())).thenReturn(Long.valueOf(1));

        Object result = sendHeaders(
                RedisConstants.COMMAND, "SREM",
                RedisConstants.KEY, "key",
                RedisConstants.VALUE, "value");

        verify(setOperations).remove("key", "value");
        assertEquals(1L, result);
    }

    @Test
    public void shouldExecuteSUNION() throws Exception {
        Set<String> resultKeys = new HashSet<>();
        resultKeys.add("key2");
        resultKeys.add("key3");

        when(setOperations.union(anyString(), anySet())).thenReturn(resultKeys);

        Set<String> keys = new HashSet<>();
        keys.add("key2");
        keys.add("key4");

        Object result = sendHeaders(
                RedisConstants.COMMAND, "SUNION",
                RedisConstants.KEY, "key",
                RedisConstants.KEYS, keys);

        verify(setOperations).union("key", keys);
        assertEquals(resultKeys, result);
    }

    @Test
    public void shouldExecuteSUNIONSTORE() throws Exception {
        Set<String> keys = new HashSet<>();
        keys.add("key2");
        keys.add("key4");

        sendHeaders(
                RedisConstants.COMMAND, "SUNIONSTORE",
                RedisConstants.KEY, "key",
                RedisConstants.KEYS, keys,
                RedisConstants.DESTINATION, "destination");

        verify(setOperations).unionAndStore("key", keys, "destination");
    }
}
