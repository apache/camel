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
package org.apache.camel.component.hashicorp.vault;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lookups of the properties function against a fake {@link VaultTemplate}, which answers the KV v2 data paths.
 */
class HashicorpVaultPropertiesFunctionTest {

    @Test
    void lookupWithoutEngineDoesNotUseTheEngineOfThePreviousLookup() {
        FakeVaultTemplate vault = new FakeVaultTemplate(
                Map.of(
                        "kv/data/api", Map.of("password", "from-kv"),
                        "secret/data/api", Map.of("password", "from-secret")));
        HashicorpVaultPropertiesFunction function = new HashicorpVaultPropertiesFunction(vault);

        assertEquals("from-kv", function.apply("kv:api#password"));
        // no engine: the default 'secret' engine, not the 'kv' engine of the lookup before
        assertEquals("from-secret", function.apply("api#password"));
        assertEquals("{password=from-secret}", function.apply("api"));
        assertEquals("from-kv", function.apply("kv:api#password"));

        assertEquals(List.of("kv/data/api", "secret/data/api", "secret/data/api", "kv/data/api"), vault.reads);
    }

    @Test
    void lookupWithoutEngineWithVersion() {
        FakeVaultTemplate vault = new FakeVaultTemplate(
                Map.of(
                        "secret/data/api?version=2", Map.of("password", "v2")));
        HashicorpVaultPropertiesFunction function = new HashicorpVaultPropertiesFunction(vault);

        assertEquals("{password=v2}", function.apply("api@2"));
        assertEquals("v2", function.apply("api#password@2"));
        assertEquals("v2", function.apply("secret:api#password@2"));
    }

    @Test
    void concurrentLookupsReadFromTheirOwnEngine() throws Exception {
        FakeVaultTemplate vault = new FakeVaultTemplate(
                Map.of(
                        "engine1/data/db", Map.of("password", "engine1"),
                        "engine2/data/db", Map.of("password", "engine2"),
                        "secret/data/db", Map.of("password", "secret")));
        HashicorpVaultPropertiesFunction function = new HashicorpVaultPropertiesFunction(vault);

        List<String> mismatches = new CopyOnWriteArrayList<>();
        List<Callable<Void>> tasks = new ArrayList<>();
        for (String engine : List.of("engine1", "engine2", "secret", "engine1", "engine2", "secret")) {
            String property = "secret".equals(engine) ? "db#password" : engine + ":db#password";
            tasks.add(() -> {
                for (int i = 0; i < 500; i++) {
                    String value = function.apply(property);
                    if (!engine.equals(value)) {
                        mismatches.add(property + " -> " + value);
                    }
                }
                return null;
            });
        }

        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        try {
            for (Future<Void> future : executor.invokeAll(tasks, 30, TimeUnit.SECONDS)) {
                future.get();
            }
        } finally {
            executor.shutdownNow();
        }

        assertTrue(mismatches.isEmpty(), "lookups read from the wrong engine: " + mismatches);
    }

    @Test
    void missingSecretWithFieldReturnsNull() {
        HashicorpVaultPropertiesFunction function = new HashicorpVaultPropertiesFunction(new FakeVaultTemplate(Map.of()));

        // same as a missing secret without a field, so optional placeholders work
        assertNull(function.apply("secret:missing"));
        assertNull(function.apply("secret:missing#password"));
        assertNull(function.apply("secret:missing#password@2"));
    }

    @Test
    void missingSecretWithFieldReturnsDefaultValue() {
        HashicorpVaultPropertiesFunction function = new HashicorpVaultPropertiesFunction(new FakeVaultTemplate(Map.of()));

        assertEquals("admin", function.apply("secret:missing#username:admin"));
        assertEquals("admin", function.apply("secret:missing#username:admin@2"));
    }

    @Test
    void missingFieldReturnsNullOrDefaultValue() {
        HashicorpVaultPropertiesFunction function = new HashicorpVaultPropertiesFunction(
                new FakeVaultTemplate(Map.of("secret/data/database", Map.of("password", "pw"))));

        assertEquals("pw", function.apply("secret:database#password"));
        assertNull(function.apply("secret:database#username"));
        assertEquals("admin", function.apply("secret:database#username:admin"));
    }

    private static final class FakeVaultTemplate extends VaultTemplate {

        private final Map<String, Map<String, Object>> secrets;
        private final List<String> reads = new CopyOnWriteArrayList<>();

        FakeVaultTemplate(Map<String, Map<String, Object>> secrets) {
            super(VaultEndpoint.create("localhost", 8200), new TokenAuthentication("token"));
            this.secrets = secrets;
        }

        @Override
        public VaultResponse read(String path) {
            reads.add(path);
            Map<String, Object> data = secrets.get(path);
            if (data == null) {
                // as Vault answers 404 for a secret that does not exist
                return null;
            }
            VaultResponse response = new VaultResponse();
            response.setData(Map.of("data", data));
            return response;
        }
    }
}
