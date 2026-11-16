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
package org.apache.camel.impl.console;

import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.AggregationStrategies;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.console.DevConsole;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.support.KeyValueIdempotentRepository;
import org.apache.camel.support.MemoryKeyValueRepository;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * The processor detail tells where a processor or a route input keeps its state, and whether the state is kept only in
 * memory, so it is lost when the integration stops. This includes a repository that the route does not configure, such
 * as the one of a file consumer with noop=true (CAMEL-27006).
 */
class ProcessorDetailStateTest extends ContextTestSupport {

    @Override
    protected boolean useJmx() {
        // the processor-detail console reads the routes and the processors from JMX
        return true;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:dedupe").routeId("dedupe")
                        .idempotentConsumer(header("id"), new KeyValueIdempotentRepository()).id("dedupe1")
                        .to("mock:dedupe");
                from("direct:custom").routeId("custom")
                        .idempotentConsumer(header("id"), new MyRepository()).id("custom1")
                        .to("mock:custom");
                from("direct:persistent").routeId("persistent")
                        .idempotentConsumer(header("id"), new KeyValueIdempotentRepository(new PersistentStore()))
                        .id("persistent1")
                        .to("mock:persistent");
                from("direct:aggregate").routeId("aggregate")
                        .aggregate(header("id"), AggregationStrategies.groupedBody()).completionSize(2).id("aggregate1")
                        .to("mock:aggregate");
                from(fileUri("?noop=true&initialDelay=0&delay=10")).routeId("files")
                        .to("mock:files");
            }
        };
    }

    @Test
    void anIdempotentConsumerNamesItsRepository() {
        JsonObject state = state("dedupe", "dedupe1");
        Assertions.assertEquals("idempotentRepository", state.getString("name"));
        Assertions.assertEquals(KeyValueIdempotentRepository.class.getName(), state.getString("type"));
        Assertions.assertEquals(Boolean.TRUE, state.getBoolean("inMemory"));
    }

    @Test
    void aRepositoryThatDoesNotTellHasNoInMemory() {
        JsonObject state = state("custom", "custom1");
        Assertions.assertEquals(MyRepository.class.getName(), state.getString("type"));
        Assertions.assertNull(state.get("inMemory"));
    }

    @Test
    void onlyTrueCountsSoARepositoryThatKeepsItsDataElsewhereHasNoInMemory() {
        // false means kept elsewhere, or cannot tell: it is left out rather than shown as persistent
        JsonObject state = state("persistent", "persistent1");
        Assertions.assertEquals(KeyValueIdempotentRepository.class.getName(), state.getString("type"));
        Assertions.assertNull(state.get("inMemory"));
    }

    @Test
    void anAggregatorWithoutARepositoryKeepsItsGroupsInMemory() {
        JsonObject state = state("aggregate", "aggregate1");
        Assertions.assertEquals("aggregationRepository", state.getString("name"));
        Assertions.assertEquals(Boolean.TRUE, state.getBoolean("inMemory"));
    }

    @Test
    void aFileConsumerWithNoopKeepsTheNamesOfTheFilesInMemory() {
        // the route configures no repository: the file consumer creates one in memory
        JsonObject from = (JsonObject) processors("files").get(0);
        Assertions.assertEquals("from", from.getString("type"));
        JsonObject state = (JsonObject) ((JsonArray) from.get("state")).get(0);
        Assertions.assertEquals("idempotentRepository", state.getString("name"));
        Assertions.assertTrue(state.getString("type").endsWith("MemoryIdempotentRepository"), state.getString("type"));
        Assertions.assertEquals(Boolean.TRUE, state.getBoolean("inMemory"));
    }

    @Test
    void aProcessorWithoutStateHasNone() {
        for (Object o : processors("dedupe")) {
            JsonObject p = (JsonObject) o;
            if ("to".equals(p.getString("type")) || "from".equals(p.getString("type"))) {
                Assertions.assertNull(p.get("state"), p.getString("type"));
            }
        }
    }

    @Test
    void theTextSaysThatTheStateIsLostWhenTheIntegrationStops() {
        DevConsole con = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("processor-detail");
        String out = (String) con.call(DevConsole.MediaType.TEXT, Map.of("routeId", "files"));
        Assertions.assertTrue(out.contains("state: idempotentRepository = "), out);
        Assertions.assertTrue(out.contains("(in memory: lost when the integration stops)"), out);
    }

    private JsonArray processors(String routeId) {
        DevConsole con = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("processor-detail");
        JsonObject out = (JsonObject) con.call(DevConsole.MediaType.JSON, Map.of("routeId", routeId));
        return (JsonArray) out.get("processors");
    }

    private JsonObject state(String routeId, String processorId) {
        for (Object o : processors(routeId)) {
            JsonObject p = (JsonObject) o;
            if (processorId.equals(p.getString("id"))) {
                JsonArray state = (JsonArray) p.get("state");
                Assertions.assertNotNull(state, processorId + " has no state");
                return (JsonObject) state.get(0);
            }
        }
        return Assertions.fail("no processor " + processorId + " in route " + routeId);
    }

    /**
     * A key-value store that keeps its data elsewhere, as one with a disk tier or a database would.
     */
    private static class PersistentStore extends MemoryKeyValueRepository {
        @Override
        public boolean isInMemory() {
            return false;
        }
    }

    /**
     * A repository that does not tell whether it keeps its keys only in memory.
     */
    private static class MyRepository extends ServiceSupport implements IdempotentRepository {

        @Override
        public boolean add(String key) {
            return true;
        }

        @Override
        public boolean contains(String key) {
            return false;
        }

        @Override
        public boolean remove(String key) {
            return true;
        }

        @Override
        public boolean confirm(String key) {
            return true;
        }

        @Override
        public void clear() {
        }
    }
}
