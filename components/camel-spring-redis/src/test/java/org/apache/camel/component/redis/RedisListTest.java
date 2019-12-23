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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.camel.spi.Registry;
import org.apache.camel.support.SimpleRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class RedisListTest extends RedisTestSupport {

    @Mock
    private RedisTemplate<String, String> redisTemplate;
    @Mock
    private ListOperations<String, String> listOperations;

    @Override
    protected Registry createCamelRegistry() throws Exception {
        when(redisTemplate.opsForList()).thenReturn(listOperations);

        Registry registry = new SimpleRegistry();
        registry.bind("redisTemplate", redisTemplate);
        return registry;
    }

    @Test
    public void shouldExecuteLPOP() throws Exception {
        when(listOperations.leftPop(anyString())).thenReturn("value");

        Object result = sendHeaders(
                RedisConstants.COMMAND, "LPOP",
                RedisConstants.KEY, "key");

        verify(listOperations).leftPop("key");
        assertEquals("value", result);
    }

    @Test
    public void shouldExecuteBLPOP() throws Exception {
        when(listOperations.leftPop(anyString(), anyLong(), any(TimeUnit.class))).thenReturn("value");

        Object result = sendHeaders(
                RedisConstants.COMMAND, "BLPOP",
                RedisConstants.KEY, "key",
                RedisConstants.TIMEOUT, "10");

        verify(listOperations).leftPop("key", 10, TimeUnit.SECONDS);
        assertEquals("value", result);
    }

    @Test
    public void shouldExecuteBRPOP() throws Exception {
        when(listOperations.rightPop(anyString(), anyLong(), any(TimeUnit.class))).thenReturn("value");

        Object result = sendHeaders(
                RedisConstants.COMMAND, "BRPOP",
                RedisConstants.KEY, "key",
                RedisConstants.TIMEOUT, "10");

        verify(listOperations).rightPop("key", 10, TimeUnit.SECONDS);
        assertEquals("value", result);
    }

    @Test
    public void shouldExecuteRPOP() throws Exception {
        when(listOperations.rightPop(anyString())).thenReturn("value");

        Object result = sendHeaders(
                RedisConstants.COMMAND, "RPOP",
                RedisConstants.KEY, "key");

        verify(listOperations).rightPop("key");
        assertEquals("value", result);
    }

    @Test
    public void shouldExecuteRPOPLPUSH() throws Exception {
        when(listOperations.rightPopAndLeftPush(anyString(), anyString())).thenReturn("value");

        Object result = sendHeaders(
                RedisConstants.COMMAND, "RPOPLPUSH",
                RedisConstants.KEY, "key",
                RedisConstants.DESTINATION, "destination");

        verify(listOperations).rightPopAndLeftPush("key", "destination");
        assertEquals("value", result);
    }

    @Test
    public void shouldExecuteBRPOPLPUSH() throws Exception {
        when(listOperations.rightPopAndLeftPush(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
                .thenReturn("value");

        Object result = sendHeaders(
                RedisConstants.COMMAND, "BRPOPLPUSH",
                RedisConstants.KEY, "key",
                RedisConstants.DESTINATION, "destination",
                RedisConstants.TIMEOUT, "10");

        verify(listOperations).rightPopAndLeftPush("key", "destination", 10, TimeUnit.SECONDS);
        assertEquals("value", result);
    }

    @Test
    public void shouldExecuteLINDEX() throws Exception {
        when(listOperations.index(anyString(), anyLong()))
                .thenReturn("value");

        Object result = sendHeaders(
                RedisConstants.COMMAND, "LINDEX",
                RedisConstants.KEY, "key",
                RedisConstants.INDEX, "2");

        verify(listOperations).index("key", 2);
        assertEquals("value", result);
    }

    @Test
    public void shouldExecuteLINSERTBEFORE() throws Exception {
        when(listOperations.leftPush(anyString(), anyString(), anyString()))
                .thenReturn(2L);

        Object result = sendHeaders(
                RedisConstants.COMMAND, "LINSERT",
                RedisConstants.KEY, "key",
                RedisConstants.VALUE, "value",
                RedisConstants.PIVOT, "pivot",
                RedisConstants.POSITION, "BEFORE");

        verify(listOperations).leftPush("key", "pivot", "value");
        assertEquals(2L, result);
    }

    @Test
    public void shouldExecuteLINSERTAFTER() throws Exception {
        when(listOperations.rightPush(anyString(), anyString(), anyString()))
                .thenReturn(2L);

        Object result = sendHeaders(
                RedisConstants.COMMAND, "LINSERT",
                RedisConstants.KEY, "key",
                RedisConstants.VALUE, "value",
                RedisConstants.PIVOT, "pivot",
                RedisConstants.POSITION, "AFTER");

        verify(listOperations).rightPush("key", "pivot", "value");
        assertEquals(2L, result);
    }

    @Test
    public void shouldExecuteLLEN() throws Exception {
        when(listOperations.size(anyString())).thenReturn(2L);

        Object result = sendHeaders(
                RedisConstants.COMMAND, "LLEN",
                RedisConstants.KEY, "key");

        verify(listOperations).size("key");
        assertEquals(2L, result);
    }

    @Test
    public void shouldExecuteLPUSH() throws Exception {
        when(listOperations.leftPush(anyString(), anyString())).thenReturn(2L);

        Object result = sendHeaders(
                RedisConstants.COMMAND, "LPUSH",
                RedisConstants.KEY, "key",
                RedisConstants.VALUE, "value");

        verify(listOperations).leftPush("key", "value");
        assertEquals(2L, result);
    }

    @Test
    public void shouldExecuteLRANGE() throws Exception {
        List<String> values = new ArrayList<>();
        values.add("value");

        when(listOperations.range(anyString(), anyLong(), anyLong()))
                .thenReturn(values);

        Object result = sendHeaders(
                RedisConstants.COMMAND, "LRANGE",
                RedisConstants.KEY, "key",
                RedisConstants.START, "0",
                RedisConstants.END, "1");

        verify(listOperations).range("key", 0, 1);
        assertEquals(values, result);
    }

    @Test
    public void shouldExecuteLREM() throws Exception {
        when(listOperations.remove(anyString(), anyLong(), anyString()))
                .thenReturn(2L);

        Object result = sendHeaders(
                RedisConstants.COMMAND, "LREM",
                RedisConstants.KEY, "key",
                RedisConstants.VALUE, "value",
                RedisConstants.COUNT, "1");

        verify(listOperations).remove("key", 1, "value");
        assertEquals(2L, result);
    }

    @Test
    public void shouldExecuteLSET() throws Exception {
        sendHeaders(
                RedisConstants.COMMAND, "LSET",
                RedisConstants.KEY, "key",
                RedisConstants.VALUE, "value",
                RedisConstants.INDEX, "1");

        verify(listOperations).set("key", 1, "value");
    }

    @Test
    public void shouldExecuteLTRIM() throws Exception {
        sendHeaders(
                RedisConstants.COMMAND, "LTRIM",
                RedisConstants.KEY, "key",
                RedisConstants.START, "1",
                RedisConstants.END, "2");

        verify(listOperations).trim("key", 1, 2);
    }

    @Test
    public void shouldExecuteRPUSH() throws Exception {
        when(listOperations.rightPush(anyString(), anyString())).thenReturn(2L);

        Object result = sendHeaders(
                RedisConstants.COMMAND, "RPUSH",
                RedisConstants.KEY, "key",
                RedisConstants.VALUE, "value");

        verify(listOperations).rightPush("key", "value");
        assertEquals(2L, result);
    }

    @Test
    public void shouldExecuteRPUSHX() throws Exception {
        when(listOperations.rightPushIfPresent(anyString(), anyString())).thenReturn(2L);

        Object result = sendHeaders(
                RedisConstants.COMMAND, "RPUSHX",
                RedisConstants.KEY, "key",
                RedisConstants.VALUE, "value");

        verify(listOperations).rightPushIfPresent("key", "value");
        assertEquals(2L, result);
    }
}
