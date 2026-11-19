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
package org.apache.camel.component.redis.processor.aggregate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectOutputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.DefaultExchangeHolder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.redisson.api.RLock;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.ByteArrayCodec;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.StringCodec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Checks how the exchanges are stored and read back, with an in-memory map in place of the Redis hashes.
 */
public class RedisAggregationRepositoryDeserializationTest extends CamelTestSupport {

    private final Map<String, byte[]> active = new HashMap<>();
    private final Map<String, byte[]> completed = new HashMap<>();
    private RedissonClient redisson;
    private RedisAggregationRepository repository;

    @BeforeEach
    void createRepository() {
        redisson = mock(RedissonClient.class);
        RMap<String, byte[]> activeMap = inMemoryMap(active);
        RMap<String, byte[]> completedMap = inMemoryMap(completed);
        when(redisson.<String, byte[]> getMap(eq("orders"), any(Codec.class))).thenReturn(activeMap);
        when(redisson.<String, byte[]> getMap(eq("orders-completed"), any(Codec.class))).thenReturn(completedMap);
        when(redisson.getLock(anyString())).thenReturn(mock(RLock.class));

        repository = new RedisAggregationRepository();
        repository.setRedisson(redisson);
        repository.setMapName("orders");
    }

    @AfterEach
    void stopRepository() {
        repository.stop();
    }

    @Test
    void testHashesUseStringFieldsAndByteArrayValues() {
        repository.start();

        ArgumentCaptor<Codec> codec = ArgumentCaptor.forClass(Codec.class);
        verify(redisson).getMap(eq("orders"), codec.capture());
        assertSame(StringCodec.INSTANCE.getMapKeyDecoder(), codec.getValue().getMapKeyDecoder());
        assertSame(ByteArrayCodec.INSTANCE.getMapValueDecoder(), codec.getValue().getMapValueDecoder());
    }

    @Test
    void testAddAndGet() {
        repository.start();

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("Hello");
        exchange.getIn().setHeader("count", 3);
        repository.add(context, "key1", exchange);

        // the value is a Java serialization stream
        byte[] value = active.get("key1");
        assertEquals((byte) 0xAC, value[0]);
        assertEquals((byte) 0xED, value[1]);

        Exchange answer = repository.get(context, "key1");
        assertNotNull(answer);
        assertEquals(exchange.getExchangeId(), answer.getExchangeId());
        assertEquals("Hello", answer.getIn().getBody());
        assertEquals(3, answer.getIn().getHeader("count"));
    }

    @Test
    void testBodyOfClassOutsideTheDefaultFilterIsNotRead() {
        repository.start();

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(URI.create("http://localhost/orders"));
        repository.add(context, "key1", exchange);

        RuntimeCamelException e = assertThrows(RuntimeCamelException.class, () -> repository.get(context, "key1"));
        assertInstanceOf(InvalidClassException.class, e.getCause());
    }

    @Test
    void testDeserializationFilterAllowsClass() {
        repository.setDeserializationFilter("java.net.URI;java.**;org.apache.camel.**;!*");
        repository.start();

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(URI.create("http://localhost/orders"));
        repository.add(context, "key1", exchange);

        Exchange answer = repository.get(context, "key1");
        assertEquals(URI.create("http://localhost/orders"), answer.getIn().getBody());
    }

    @Test
    void testRecoverAppliesTheFilter() {
        repository.start();

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(URI.create("http://localhost/orders"));
        completed.put(exchange.getExchangeId(), serialize(DefaultExchangeHolder.marshal(exchange, true, false)));

        String id = exchange.getExchangeId();
        RuntimeCamelException e = assertThrows(RuntimeCamelException.class, () -> repository.recover(context, id));
        assertInstanceOf(InvalidClassException.class, e.getCause());
    }

    @Test
    void testValueInAnotherFormatIsNotRead() {
        repository.start();

        // not a Java serialization stream, such as a value stored by an older version
        active.put("key1", new byte[] { 1, 0, 'o', 'r', 'g' });

        RuntimeCamelException e = assertThrows(RuntimeCamelException.class, () -> repository.get(context, "key1"));
        assertTrue(e.getMessage().contains("is not a serialized exchange"), e.getMessage());
    }

    @Test
    void testValueThatIsNotAnExchangeIsNotUsed() {
        repository.start();

        active.put("key1", serialize("Hello"));

        RuntimeCamelException e = assertThrows(RuntimeCamelException.class, () -> repository.get(context, "key1"));
        assertTrue(e.getMessage().contains("java.lang.String"), e.getMessage());
    }

    @Test
    void testWarnAtStartForEntriesInAnotherFormat() {
        // such as entries stored by an older version, which are not read
        active.put("\u0003key1\uFFFD", new byte[] { 1, 0, 'o', 'r', 'g' });
        active.put("\u0003key2\uFFFD", new byte[] { 1, 0, 'o', 'r', 'g' });
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("Hello");
        active.put("key3", serialize(DefaultExchangeHolder.marshal(exchange, true, false)));
        completed.put("\u0003id1\uFFFD", new byte[] { 1, 0, 'o', 'r', 'g' });

        Logger logger = (Logger) LogManager.getLogger(RedisAggregationRepository.class);
        LogEventCaptor captor = new LogEventCaptor();
        logger.addAppender(captor);
        try {
            repository.start();
        } finally {
            logger.removeAppender(captor);
        }

        assertTrue(repository.isStarted());
        List<String> warnings = captor.events.stream()
                .filter(event -> event.getLevel() == Level.WARN)
                .map(event -> event.getMessage().getFormattedMessage())
                .toList();
        assertEquals(2, warnings.size(), warnings.toString());
        assertTrue(warnings.get(0).contains("The Redis hash orders has entries") && warnings.get(0).contains("(count: 2)"),
                warnings.get(0));
        assertTrue(warnings.get(0).contains("upgrade guide"), warnings.get(0));
        assertTrue(warnings.get(1).contains("The Redis hash orders-completed has entries")
                && warnings.get(1).contains("(count: 1)"), warnings.get(1));
        // the fields and values are not logged
        assertTrue(warnings.stream().noneMatch(w -> w.contains("key1") || w.contains("\uFFFD")), warnings.toString());
    }

    @Test
    void testNoWarnAtStartForExchanges() {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("Hello");
        active.put("key1", serialize(DefaultExchangeHolder.marshal(exchange, true, false)));

        Logger logger = (Logger) LogManager.getLogger(RedisAggregationRepository.class);
        LogEventCaptor captor = new LogEventCaptor();
        logger.addAppender(captor);
        try {
            repository.start();
        } finally {
            logger.removeAppender(captor);
        }

        assertTrue(captor.events.stream().noneMatch(event -> event.getLevel() == Level.WARN));
    }

    @SuppressWarnings("unchecked")
    private static RMap<String, byte[]> inMemoryMap(Map<String, byte[]> store) {
        RMap<String, byte[]> map = mock(RMap.class);
        when(map.get(any())).thenAnswer(i -> store.get(i.<String> getArgument(0)));
        when(map.put(anyString(), any())).thenAnswer(i -> store.put(i.getArgument(0), i.getArgument(1)));
        when(map.keySet()).thenAnswer(i -> Set.copyOf(store.keySet()));
        when(map.entrySet(anyInt())).thenAnswer(i -> Set.copyOf(store.entrySet()));
        return map;
    }

    private static final class LogEventCaptor extends AbstractAppender {
        private final List<LogEvent> events = new CopyOnWriteArrayList<>();

        LogEventCaptor() {
            super("RedisAggregationRepositoryLogCaptor", null, null, false, Property.EMPTY_ARRAY);
            start();
        }

        @Override
        public void append(LogEvent event) {
            events.add(event.toImmutable());
        }
    }

    private static byte[] serialize(Object value) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(value);
            out.flush();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
