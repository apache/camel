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
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.camel.impl.JndiRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.query.SortQuery;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class RedisKeyTest extends RedisTestSupport {

    @Mock
    private RedisTemplate<String, Integer> redisTemplate;

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();
        registry.bind("redisTemplate", redisTemplate);
        return registry;
    }

    @Test
    public void shouldExecuteDEL() throws Exception {
        Collection<String> keys = new HashSet<>();
        keys.add("key1");
        keys.add("key2");
        sendHeaders(
                RedisConstants.COMMAND, "DEL",
                RedisConstants.KEYS, keys);

        verify(redisTemplate).delete(keys);
    }

    @Test
    public void shouldExecuteEXISTS() throws Exception {
        when(redisTemplate.hasKey(anyString())).thenReturn(true);

        Object result = sendHeaders(
                RedisConstants.COMMAND, "EXISTS",
                RedisConstants.KEY, "key");

        verify(redisTemplate).hasKey("key");
        assertEquals(true, result);

    }

    @Test
    public void shouldExecuteEXPIRE() throws Exception {
        when(redisTemplate.expire(anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);

        Object result = sendHeaders(
                RedisConstants.COMMAND, "EXPIRE",
                RedisConstants.KEY, "key",
                RedisConstants.TIMEOUT, "10");

        verify(redisTemplate).expire("key", 10L, TimeUnit.SECONDS);
        assertEquals(true, result);
    }

    @Test
    public void shouldExecuteEXPIREAT() throws Exception {
        when(redisTemplate.expireAt(anyString(), any(Date.class))).thenReturn(true);
        long unixTime = System.currentTimeMillis() / 1000L;

        Object result = sendHeaders(
                RedisConstants.COMMAND, "EXPIREAT",
                RedisConstants.KEY, "key",
                RedisConstants.TIMESTAMP, unixTime);

        verify(redisTemplate).expireAt("key", new Date(unixTime * 1000L));
        assertEquals(true, result);
    }

    @Test
    public void shouldExecuteKEYS() throws Exception {
        Set<String> keys = new HashSet<>();
        keys.add("key1");
        keys.add("key2");
        when(redisTemplate.keys(anyString())).thenReturn(keys);

        Object result = sendHeaders(
                RedisConstants.COMMAND, "KEYS",
                RedisConstants.PATTERN, "key*");

        verify(redisTemplate).keys("key*");
        assertEquals(keys, result);
    }

    @Test
    public void shouldExecuteMOVE() throws Exception {
        when(redisTemplate.move(anyString(), anyInt())).thenReturn(true);

        Object result = sendHeaders(
                RedisConstants.COMMAND, "MOVE",
                RedisConstants.KEY, "key",
                RedisConstants.DB, "2");

        verify(redisTemplate).move("key", 2);
        assertEquals(true, result);
    }

    @Test
    public void shouldExecutePERSIST() throws Exception {
        when(redisTemplate.persist(anyString())).thenReturn(true);

        Object result = sendHeaders(
                RedisConstants.COMMAND, "PERSIST",
                RedisConstants.KEY, "key");

        verify(redisTemplate).persist("key");
        assertEquals(true, result);
    }

    @Test
    public void shouldExecutePEXPIRE() throws Exception {
        when(redisTemplate.expire(anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);

        Object result = sendHeaders(
                RedisConstants.COMMAND, "PEXPIRE",
                RedisConstants.KEY, "key",
                RedisConstants.TIMEOUT, "10");

        verify(redisTemplate).expire("key", 10L, TimeUnit.MILLISECONDS);
        assertEquals(true, result);
    }

    @Test
    public void shouldExecutePEXPIREAT() throws Exception {
        when(redisTemplate.expireAt(anyString(), any(Date.class))).thenReturn(true);

        long millis = System.currentTimeMillis();
        Object result = sendHeaders(
                RedisConstants.COMMAND, "PEXPIREAT",
                RedisConstants.KEY, "key",
                RedisConstants.TIMESTAMP, millis);

        verify(redisTemplate).expireAt("key", new Date(millis));
        assertEquals(true, result);

    }

    @Test
    public void shouldExecuteRANDOMKEY() throws Exception {
        when(redisTemplate.randomKey()).thenReturn("key");

        Object result = sendHeaders(
                RedisConstants.COMMAND, "RANDOMKEY");

        verify(redisTemplate).randomKey();
        assertEquals("key", result);
    }

    @Test
    public void shouldExecuteRENAME() throws Exception {
        sendHeaders(
                RedisConstants.COMMAND, "RENAME",
                RedisConstants.KEY, "key",
                RedisConstants.VALUE, "newkey");

        verify(redisTemplate).rename("key", "newkey");
    }

    @Test
    public void shouldExecuteRENAMENX() throws Exception {
        when(redisTemplate.renameIfAbsent(anyString(), anyString())).thenReturn(true);

        Object result = sendHeaders(
                RedisConstants.COMMAND, "RENAMENX",
                RedisConstants.KEY, "key",
                RedisConstants.VALUE, "newkey");

        verify(redisTemplate).renameIfAbsent("key", "newkey");
        assertEquals(true, result);
    }

    @Test
    public void shouldExecuteSORT() throws Exception {
        List<Integer> list = new ArrayList<>();
        list.add(5);
        when(redisTemplate.sort(ArgumentMatchers.<SortQuery<String>>any())).thenReturn(list);

        Object result = sendHeaders(
                RedisConstants.COMMAND, "SORT",
                RedisConstants.KEY, "key");

        verify(redisTemplate).sort(ArgumentMatchers.<SortQuery<String>>any());
        assertEquals(list, result);
    }

    @Test
    public void shouldExecuteTTL() throws Exception {
        when(redisTemplate.getExpire(anyString())).thenReturn(2L);

        Object result = sendHeaders(
                RedisConstants.COMMAND, "TTL",
                RedisConstants.KEY, "key");

        verify(redisTemplate).getExpire("key");
        assertEquals(2L, result);
    }

    @Test
    public void shouldExecuteTYPE() throws Exception {
        when(redisTemplate.type(anyString())).thenReturn(DataType.STRING);

        Object result = sendHeaders(
                RedisConstants.COMMAND, "TYPE",
                RedisConstants.KEY, "key");

        verify(redisTemplate).type("key");
        assertEquals(DataType.STRING.toString(), result);
    }
}
