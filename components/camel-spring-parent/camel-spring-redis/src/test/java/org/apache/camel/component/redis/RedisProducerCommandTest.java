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

import org.apache.camel.Exchange;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@MockitoSettings(strictness = Strictness.LENIENT)
public class RedisProducerCommandTest extends RedisTestSupport {

    private static final String OLD_BODY = "Other body";

    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private HashOperations<String, String, String> hashOperations;
    @Mock
    private ListOperations<String, String> listOperations;
    @Mock
    private SetOperations<String, String> setOperations;
    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @Override
    protected void bindToRegistry(Registry registry) throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.<String, String> opsForHash()).thenReturn(hashOperations);
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);

        registry.bind("redisTemplate", redisTemplate);
    }

    @Test
    public void shouldFailUnknownCommand() {
        Exchange exchange = send(OLD_BODY, RedisConstants.COMMAND, "NOSUCH", RedisConstants.KEY, "key");

        IllegalArgumentException cause = assertInstanceOf(IllegalArgumentException.class, exchange.getException());
        assertTrue(cause.getMessage().contains("Unknown command NOSUCH"), cause.getMessage());
        verifyNoInteractions(redisTemplate);
    }

    @ParameterizedTest
    @ValueSource(strings = { "FLUSHDB", "flushdb", "AUTH", "RENAMEX", "SUBSTR" })
    public void shouldFailCommandTheProducerDoesNotSupport(String command) {
        Exchange exchange = send(OLD_BODY, RedisConstants.COMMAND, command, RedisConstants.KEY, "key");

        UnsupportedOperationException cause = assertInstanceOf(UnsupportedOperationException.class, exchange.getException());
        assertTrue(cause.getMessage().contains("command " + command.toUpperCase()), cause.getMessage());
        assertTrue(cause.getMessage().contains("does not support"), cause.getMessage());
        verifyNoInteractions(redisTemplate);
    }

    @ParameterizedTest
    @ValueSource(strings = { "SUBSCRIBE", "PSUBSCRIBE" })
    public void shouldFailConsumerCommandInProducer(String command) {
        Exchange exchange = send(OLD_BODY, RedisConstants.COMMAND, command);

        UnsupportedOperationException cause = assertInstanceOf(UnsupportedOperationException.class, exchange.getException());
        assertTrue(cause.getMessage().contains(command), cause.getMessage());
        assertTrue(cause.getMessage().contains("consumer"), cause.getMessage());
    }

    @Test
    public void shouldExecuteCommandInMixedCase() {
        when(valueOperations.get("key")).thenReturn("value");

        Exchange exchange = send(OLD_BODY, RedisConstants.COMMAND, "Get", RedisConstants.KEY, "key");

        assertNull(exchange.getException());
        verify(valueOperations).get("key");
        assertEquals("value", exchange.getMessage().getBody());
    }

    @ParameterizedTest
    @EnumSource(Command.class)
    public void shouldDispatchEveryCommandTheProducerSupports(Command command) throws Exception {
        RedisProducer producer = mock(RedisProducer.class);
        Exchange exchange = new DefaultExchange(context);

        new RedisProducerInvokeOnHeaderFactory().invoke(producer, command.name(), exchange, null);

        boolean dispatched = !mockingDetails(producer).getInvocations().isEmpty();
        assertEquals(!RedisProducer.UNSUPPORTED_COMMANDS.contains(command), dispatched,
                "The command " + command + " must have an @InvokeOnHeader method or be listed as unsupported");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "GET", "GETSET", "HGET", "LINDEX", "LPOP", "RPOP", "RPOPLPUSH", "SPOP", "SRANDMEMBER",
            "ZSCORE", "ZRANK", "ZREVRANK" })
    public void shouldSetBodyToNullWhenCommandFindsNoValue(String command) {
        // a mock returns 0 for a Double or Long, while Redis returns null for a missing member
        when(zSetOperations.score("nope", "value")).thenReturn(null);
        when(zSetOperations.rank("nope", "value")).thenReturn(null);
        when(zSetOperations.reverseRank("nope", "value")).thenReturn(null);

        Exchange exchange = send(OLD_BODY,
                RedisConstants.COMMAND, command,
                RedisConstants.KEY, "nope",
                RedisConstants.FIELD, "field",
                RedisConstants.INDEX, 0L,
                RedisConstants.DESTINATION, "destination",
                RedisConstants.VALUE, "value");

        assertNull(exchange.getException());
        assertNull(exchange.getMessage().getBody());
    }

    @Test
    public void shouldKeepBodyForCommandWithoutResult() {
        Exchange exchange = send(OLD_BODY,
                RedisConstants.COMMAND, "SET",
                RedisConstants.KEY, "key",
                RedisConstants.VALUE, "value");

        assertNull(exchange.getException());
        verify(valueOperations).set("key", "value");
        assertEquals(OLD_BODY, exchange.getMessage().getBody());
    }

    private Exchange send(Object body, Object... headers) {
        return template.send(exchange -> {
            exchange.getIn().setBody(body);
            for (int i = 0; i < headers.length; i = i + 2) {
                exchange.getIn().setHeader(headers[i].toString(), headers[i + 1]);
            }
        });
    }
}
