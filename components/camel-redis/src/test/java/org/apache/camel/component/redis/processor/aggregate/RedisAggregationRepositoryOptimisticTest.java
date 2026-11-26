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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.apache.camel.AggregationStrategy;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.aggregate.OptimisticLockRetryPolicy;
import org.apache.camel.spi.OptimisticLockingAggregationRepository.OptimisticLockingException;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RMap;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.ByteArrayCodec;
import org.redisson.client.codec.Codec;
import org.redisson.client.handler.State;
import org.redisson.codec.Kryo5Codec;
import org.redisson.codec.SerializationCodec;
import org.redisson.config.Config;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The repository with optimistic locking through the Aggregate EIP, with an in-memory stand-in for the Redis hashes
 * that stores the values encoded by the codec of the hash and compares them by their bytes, as Redis does.
 */
public class RedisAggregationRepositoryOptimisticTest extends CamelTestSupport {

    /** Changes the old exchange and returns it, as the Aggregate EIP documentation shows. */
    private static final AggregationStrategy CHANGE_OLD = (oldExchange, newExchange) -> {
        if (oldExchange == null) {
            return newExchange;
        }
        oldExchange.getIn().setBody(join(oldExchange, newExchange));
        return oldExchange;
    };

    /** Leaves the old exchange as it is and returns the new exchange. */
    private static final AggregationStrategy RETURN_NEW = (oldExchange, newExchange) -> {
        if (oldExchange != null) {
            newExchange.getIn().setBody(join(oldExchange, newExchange));
        }
        return newExchange;
    };

    /** Keeps a list body and adds the new message to it in place. */
    @SuppressWarnings("unchecked")
    private static final AggregationStrategy ADD_TO_LIST = (oldExchange, newExchange) -> {
        if (oldExchange == null) {
            List<String> list = new ArrayList<>();
            list.add(newExchange.getIn().getBody(String.class));
            newExchange.getIn().setBody(list);
            return newExchange;
        }
        oldExchange.getIn().getBody(List.class).add(newExchange.getIn().getBody(String.class));
        return oldExchange;
    };

    /** Keeps a HashSet body and adds the new message to it in place. */
    @SuppressWarnings("unchecked")
    private static final AggregationStrategy ADD_TO_SET = (oldExchange, newExchange) -> {
        if (oldExchange == null) {
            Set<String> set = new HashSet<>();
            set.add(newExchange.getIn().getBody(String.class));
            newExchange.getIn().setBody(set);
            return newExchange;
        }
        oldExchange.getIn().getBody(Set.class).add(newExchange.getIn().getBody(String.class));
        return oldExchange;
    };

    // the Redis hashes, guarded by themselves as Redis runs one command or script at a time
    private final Map<String, Map<String, Object>> redis = new HashMap<>();
    // the codec of each hash, to read the fields that the script is given
    private final Map<String, Codec> codecs = new HashMap<>();
    private RedissonClient redisson;
    private final List<RedisAggregationRepository> repositories = new ArrayList<>();

    @BeforeEach
    void createRedis() {
        redisson = mock(RedissonClient.class);
        when(redisson.<String, Object> getMap(anyString(), any(Codec.class)))
                .thenAnswer(i -> {
                    synchronized (redis) {
                        codecs.put(i.getArgument(0), i.getArgument(1));
                    }
                    return inMemoryMap(hash(i.getArgument(0)), i.getArgument(1));
                });
        Config config = mock(Config.class);
        when(redisson.getConfig()).thenReturn(config);
        RScript script = mock(RScript.class);
        when(redisson.getScript(ByteArrayCodec.INSTANCE)).thenReturn(script);
        when(script.eval(any(RScript.Mode.class), anyString(), any(RScript.ReturnType.class), anyList(),
                any(Object[].class)))
                .thenAnswer(i -> {
                    Object[] args = i.getArguments();
                    Object[] values = args.length == 5 && args[4] instanceof Object[] array
                            ? array : Arrays.copyOfRange(args, 4, args.length);
                    return optimisticRemoveScript(i.getArgument(1), i.getArgument(3), values);
                });
    }

    @AfterEach
    void stopRepositories() {
        repositories.forEach(RedisAggregationRepository::stop);
    }

    @Test
    void testCompletionSizeWithStrategyThatChangesOldExchange() throws Exception {
        assertGroupsComplete("changeOld", CHANGE_OLD, true);
    }

    @Test
    void testCompletionSizeWithStrategyThatChangesOldExchangeWithoutRecovery() throws Exception {
        assertGroupsComplete("changeOldNoRecovery", CHANGE_OLD, false);
    }

    @Test
    void testCompletionSizeWithStrategyThatReturnsNewExchangeWithoutRecovery() throws Exception {
        assertGroupsComplete("returnNewNoRecovery", RETURN_NEW, false);
    }

    @Test
    void testCompletionSizeWithCodecAndStrategyThatChangesTheBodyInPlace() throws Exception {
        // the exchange that get() returns must not share the body with the value it read and compares with
        String name = "codecInPlace";
        RedisAggregationRepository repository = repository(name, true, new Kryo5Codec());
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:" + name)
                        .aggregate(header("id"), ADD_TO_LIST).aggregationRepository(repository)
                        .optimisticLocking().optimisticLockRetryPolicy(retryPolicy())
                        .completionSize(3)
                        .to("mock:" + name);
            }
        });
        MockEndpoint mock = getMockEndpoint("mock:" + name);
        mock.expectedBodiesReceivedInAnyOrder(List.of("a", "b", "c"), List.of("d", "e", "f"));

        for (String body : List.of("a", "d", "b", "e", "c", "f")) {
            template.sendBodyAndHeader("direct:" + name, body, "id", "abc".contains(body) ? "K1" : "K2");
        }

        MockEndpoint.assertIsSatisfied(context, 20, TimeUnit.SECONDS);
        assertTrue(hash(name).isEmpty(), "groups left: " + hash(name).keySet());
        await().atMost(10, TimeUnit.SECONDS).until(() -> hash(name + "-completed").isEmpty());
    }

    @Test
    void testCompletionSizeWithKryoCodecAndHashSetBody() throws Exception {
        // Kryo reads a HashSet back with another capacity, so the decoded group encoded again has its elements in
        // another order once the set has a dozen of them: the compare must use the bytes that were read
        completeOneGroupWithCodec("kryoHashSet", new Kryo5Codec(), ADD_TO_SET, 20);
    }

    @Test
    void testCompletionSizeWithSerializationCodec() throws Exception {
        // Java serialization of a HashMap writes its capacity, which the decoded group does not keep, so the decoded
        // group encoded again differs from the stored bytes from the second message on
        completeOneGroupWithCodec("serializationCodec", new SerializationCodec(), ADD_TO_LIST, 3);
    }

    @Test
    void testCompletionTimeout() throws Exception {
        RedisAggregationRepository repository = repository("timeout", true);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:timeout")
                        .aggregate(header("id"), CHANGE_OLD).aggregationRepository(repository)
                        .optimisticLocking().optimisticLockRetryPolicy(retryPolicy())
                        .completionTimeout(200).completionTimeoutCheckerInterval(50)
                        .to("mock:timeout");
            }
        });
        MockEndpoint mock = getMockEndpoint("mock:timeout");
        mock.expectedBodiesReceivedInAnyOrder("a", "b+c");

        template.sendBodyAndHeader("direct:timeout", "a", "id", "K1");
        template.sendBodyAndHeader("direct:timeout", "b", "id", "K2");
        template.sendBodyAndHeader("direct:timeout", "c", "id", "K2");

        MockEndpoint.assertIsSatisfied(context, 20, TimeUnit.SECONDS);
        assertTrue(hash("timeout").isEmpty(), "groups left: " + hash("timeout").keySet());
        await().atMost(10, TimeUnit.SECONDS).until(() -> hash("timeout-completed").isEmpty());
    }

    @Test
    void testTwoRepositoriesSharingTheHashes() throws Exception {
        // two aggregators with their own repository on the same hashes, as two Camel instances on the same Redis
        RedisAggregationRepository first = repository("shared", true);
        RedisAggregationRepository second = repository("shared", true);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:first")
                        .aggregate(header("id"), CHANGE_OLD).aggregationRepository(first)
                        .optimisticLocking().optimisticLockRetryPolicy(retryPolicy())
                        .completionSize(2).to("mock:shared");
                from("direct:second")
                        .aggregate(header("id"), CHANGE_OLD).aggregationRepository(second)
                        .optimisticLocking().optimisticLockRetryPolicy(retryPolicy())
                        .completionSize(2).to("mock:shared");
            }
        });
        int groups = 20;
        MockEndpoint mock = getMockEndpoint("mock:shared");
        mock.expectedMessageCount(groups);

        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Future<?>> sends = new ArrayList<>();
            for (int i = 0; i < groups; i++) {
                String key = "K" + i;
                sends.add(executor.submit(() -> template.sendBodyAndHeader("direct:first", "a", "id", key)));
                sends.add(executor.submit(() -> template.sendBodyAndHeader("direct:second", "b", "id", key)));
            }
            for (Future<?> send : sends) {
                send.get(30, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        MockEndpoint.assertIsSatisfied(context, 20, TimeUnit.SECONDS);
        Set<String> bodies = new HashSet<>();
        for (Exchange exchange : mock.getReceivedExchanges()) {
            String body = exchange.getIn().getBody(String.class);
            assertTrue(body.equals("a+b") || body.equals("b+a"), body);
            bodies.add(exchange.getIn().getHeader("id", String.class));
        }
        assertEquals(groups, bodies.size());
        assertTrue(hash("shared").isEmpty(), "groups left: " + hash("shared").keySet());
    }

    @Test
    void testRemoveOfGroupWithCorrelationKeySetByAggregator() {
        RedisAggregationRepository repository = repository("removeRead", true);
        repository.add(context, "K", null, exchange("a", "K"));

        Exchange group = repository.get(context, "K");
        // as the Aggregate EIP does before it removes a completed group
        group.setProperty(Exchange.AGGREGATED_CORRELATION_KEY, "K");
        repository.remove(context, "K", group);

        assertTrue(hash("removeRead").isEmpty());
        assertEquals(Set.of(group.getExchangeId()), hash("removeRead-completed").keySet());
    }

    @Test
    void testCompletedEntryIsTheGivenExchange() throws Exception {
        RedisAggregationRepository repository = repository("completedEntry", true);
        repository.add(context, "K", null, exchange("a", "K"));

        Exchange group = repository.get(context, "K");
        group.getIn().setBody("a+b");
        group.setProperty(Exchange.AGGREGATED_CORRELATION_KEY, "K");
        repository.remove(context, "K", group);

        // the completed entry, which the recovery sends after a crash, has the last message and the correlation key
        Exchange recovered = repository.recover(context, group.getExchangeId());
        assertEquals("a+b", recovered.getIn().getBody());
        assertEquals("K", recovered.getProperty(Exchange.AGGREGATED_CORRELATION_KEY));
    }

    @Test
    void testRemoveWhenEqualValuesSerializeDifferently() {
        RedisAggregationRepository repository = repository("sameValue", false);
        // the header and the property are the same String instance, which Java serialization writes once
        String key = "K";
        Exchange first = exchange("a", key);
        first.setProperty(Exchange.AGGREGATED_CORRELATION_KEY, key);
        repository.add(context, key, null, first);

        Exchange group = repository.get(context, key);
        // an equal value, but another instance than the header
        group.setProperty(Exchange.AGGREGATED_CORRELATION_KEY, new String(key));
        repository.remove(context, key, group);

        assertTrue(hash("sameValue").isEmpty());
    }

    @Test
    void testStaleReadFailsToUpdateOrRemove() {
        RedisAggregationRepository repository = repository("stale", true);
        repository.add(context, "K", null, exchange("a", "K"));

        Exchange stale = repository.get(context, "K");
        Exchange staleForRemove = repository.get(context, "K");
        Exchange current = repository.get(context, "K");
        current.getIn().setBody("a+b");
        repository.add(context, "K", current, current);

        Exchange update = exchange("a+c", "K");
        assertThrows(OptimisticLockingException.class, () -> repository.add(context, "K", stale, update));
        assertThrows(OptimisticLockingException.class, () -> repository.remove(context, "K", staleForRemove));
        assertEquals("a+b", repository.get(context, "K").getIn().getBody());
        assertTrue(hash("stale-completed").isEmpty());
    }

    @Test
    void testExchangeNotReadByGetIsComparedAsItIs() {
        RedisAggregationRepository repository = repository("notRead", false);
        Exchange first = exchange("a", "K");
        repository.add(context, "K", null, first);

        // an exchange that the repository did not return, compared as it is now (as before)
        repository.add(context, "K", first, exchange("a+b", "K"));
        assertEquals("a+b", repository.get(context, "K").getIn().getBody());
        Exchange changed = exchange("a+c", "K");
        assertThrows(OptimisticLockingException.class, () -> repository.add(context, "K", changed, changed));
    }

    private void assertGroupsComplete(String name, AggregationStrategy strategy, boolean useRecovery)
            throws Exception {
        RedisAggregationRepository repository = repository(name, useRecovery);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:" + name)
                        .aggregate(header("id"), strategy).aggregationRepository(repository)
                        .optimisticLocking().optimisticLockRetryPolicy(retryPolicy())
                        .completionSize(2)
                        .to("mock:" + name);
            }
        });
        MockEndpoint mock = getMockEndpoint("mock:" + name);
        mock.expectedBodiesReceivedInAnyOrder("a+b", "c+d");

        template.sendBodyAndHeader("direct:" + name, "a", "id", "K1");
        template.sendBodyAndHeader("direct:" + name, "c", "id", "K2");
        template.sendBodyAndHeader("direct:" + name, "b", "id", "K1");
        template.sendBodyAndHeader("direct:" + name, "d", "id", "K2");

        MockEndpoint.assertIsSatisfied(context, 20, TimeUnit.SECONDS);
        assertTrue(hash(name).isEmpty(), "groups left: " + hash(name).keySet());
        if (useRecovery) {
            // the completed exchanges are confirmed once they are done
            await().atMost(10, TimeUnit.SECONDS).until(() -> hash(name + "-completed").isEmpty());
        }
    }

    private RedisAggregationRepository repository(String mapName, boolean useRecovery) {
        return repository(mapName, useRecovery, null);
    }

    private RedisAggregationRepository repository(String mapName, boolean useRecovery, Codec codec) {
        RedisAggregationRepository repository = new RedisAggregationRepository();
        repository.setRedisson(redisson);
        repository.setCodec(codec);
        repository.setMapName(mapName);
        repository.setOptimistic(true);
        repository.setUseRecovery(useRecovery);
        repository.setRecoveryInterval(60_000);
        repository.start();
        repositories.add(repository);
        return repository;
    }

    /**
     * Aggregates one group of the given size with the codec, and checks that it completes with all the messages.
     */
    private void completeOneGroupWithCodec(String name, Codec codec, AggregationStrategy strategy, int size)
            throws Exception {
        RedisAggregationRepository repository = repository(name, true, codec);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:" + name)
                        .aggregate(header("id"), strategy).aggregationRepository(repository)
                        .optimisticLocking().optimisticLockRetryPolicy(retryPolicy())
                        .completionSize(size)
                        .to("mock:" + name);
            }
        });
        MockEndpoint mock = getMockEndpoint("mock:" + name);
        mock.expectedMessageCount(1);

        Set<String> messages = new HashSet<>();
        for (int i = 0; i < size; i++) {
            // spread hash codes, so that the elements of a HashSet are ordered by the capacity of the set
            String body = "message-" + i + "-" + Integer.toHexString(i * 0x9E3779B1);
            messages.add(body);
            template.sendBodyAndHeader("direct:" + name, body, "id", "K");
        }

        MockEndpoint.assertIsSatisfied(context, 20, TimeUnit.SECONDS);
        Collection<?> body = mock.getReceivedExchanges().get(0).getIn().getBody(Collection.class);
        assertEquals(size, body.size());
        assertEquals(messages, new HashSet<>(body));
        assertTrue(hash(name).isEmpty(), "groups left: " + hash(name).keySet());
        await().atMost(10, TimeUnit.SECONDS).until(() -> hash(name + "-completed").isEmpty());
    }

    private static OptimisticLockRetryPolicy retryPolicy() {
        // bounded, so a compare that never matches fails the exchange instead of retrying forever
        OptimisticLockRetryPolicy policy = new OptimisticLockRetryPolicy();
        policy.setMaximumRetries(20);
        policy.setRetryDelay(1);
        policy.setMaximumRetryDelay(10);
        return policy;
    }

    private Exchange exchange(String body, String key) {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(body);
        exchange.getIn().setHeader("id", key);
        return exchange;
    }

    private static String join(Exchange oldExchange, Exchange newExchange) {
        return oldExchange.getIn().getBody(String.class) + "+" + newExchange.getIn().getBody(String.class);
    }

    private Map<String, Object> hash(String name) {
        synchronized (redis) {
            return redis.computeIfAbsent(name, n -> new HashMap<>());
        }
    }

    /**
     * The script of the repository: compares the value of the field in the first hash with the expected value, and if
     * they are equal, sets the value that the script names (the stored value or an argument) under the exchange id in
     * the second hash, and deletes the field.
     */
    private Long optimisticRemoveScript(String script, List<Object> keys, Object[] values) {
        Matcher set = Pattern.compile("HSET', KEYS\\[2\\], ARGV\\[3\\], (current|ARGV\\[(\\d)\\])").matcher(script);
        assertTrue(set.find(), script);
        Map<String, Object> active = hash((String) keys.get(0));
        Map<String, Object> completed = hash((String) keys.get(1));
        synchronized (redis) {
            String field = (String) decodeField(codecs.get((String) keys.get(0)), values[0]);
            Object current = active.get(field);
            if (!same(current, values[1])) {
                return 0L;
            }
            Object value = set.group(2) == null ? current : values[Integer.parseInt(set.group(2)) - 1];
            completed.put((String) decodeField(codecs.get((String) keys.get(1)), values[2]), value);
            active.remove(field);
            return 1L;
        }
    }

    /**
     * A hash that stores the values encoded by the value encoder of the codec, and gives back the values decoded, as
     * Redisson and Redis do.
     */
    @SuppressWarnings("unchecked")
    private RMap<String, Object> inMemoryMap(Map<String, Object> store, Codec codec) {
        RMap<String, Object> map = mock(RMap.class);
        when(map.get(any())).thenAnswer(i -> {
            synchronized (redis) {
                return decode(codec, store.get(i.<String> getArgument(0)));
            }
        });
        when(map.put(anyString(), any())).thenAnswer(i -> {
            synchronized (redis) {
                return decode(codec, store.put(i.getArgument(0), encode(codec, i.getArgument(1))));
            }
        });
        when(map.putIfAbsent(anyString(), any())).thenAnswer(i -> {
            synchronized (redis) {
                return decode(codec, store.putIfAbsent(i.getArgument(0), encode(codec, i.getArgument(1))));
            }
        });
        when(map.replace(anyString(), any(), any())).thenAnswer(i -> {
            synchronized (redis) {
                if (!same(store.get(i.<String> getArgument(0)), encode(codec, i.getArgument(1)))) {
                    return false;
                }
                store.put(i.getArgument(0), encode(codec, i.getArgument(2)));
                return true;
            }
        });
        when(map.remove(anyString(), any())).thenAnswer(i -> {
            synchronized (redis) {
                if (!same(store.get(i.<String> getArgument(0)), encode(codec, i.getArgument(1)))) {
                    return false;
                }
                store.remove(i.<String> getArgument(0));
                return true;
            }
        });
        when(map.remove(anyString())).thenAnswer(i -> {
            synchronized (redis) {
                return decode(codec, store.remove(i.<String> getArgument(0)));
            }
        });
        when(map.keySet()).thenAnswer(i -> {
            synchronized (redis) {
                return Set.copyOf(store.keySet());
            }
        });
        when(map.entrySet(anyInt())).thenAnswer(i -> {
            synchronized (redis) {
                Map<String, Object> decoded = new HashMap<>();
                store.forEach((field, value) -> decoded.put(field, decode(codec, value)));
                return Set.copyOf(decoded.entrySet());
            }
        });
        when(map.size()).thenAnswer(i -> {
            synchronized (redis) {
                return store.size();
            }
        });
        return map;
    }

    /**
     * Whether the stored value and the expected value are equal, as Redis compares them: by their bytes.
     */
    private static boolean same(Object stored, Object expected) {
        return stored instanceof byte[] a && expected instanceof byte[] b && Arrays.equals(a, b);
    }

    private static byte[] encode(Codec codec, Object value) {
        ByteBuf buf = null;
        try {
            buf = codec.getMapValueEncoder().encode(value);
            byte[] bytes = new byte[buf.readableBytes()];
            buf.getBytes(buf.readerIndex(), bytes);
            return bytes;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            if (buf != null) {
                buf.release();
            }
        }
    }

    private static Object decodeField(Codec codec, Object field) {
        ByteBuf buf = Unpooled.wrappedBuffer((byte[]) field);
        try {
            return codec.getMapKeyDecoder().decode(buf, new State());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            buf.release();
        }
    }

    private static Object decode(Codec codec, Object stored) {
        if (stored == null) {
            return null;
        }
        ByteBuf buf = Unpooled.wrappedBuffer((byte[]) stored);
        try {
            return codec.getMapValueDecoder().decode(buf, new State());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            buf.release();
        }
    }
}
