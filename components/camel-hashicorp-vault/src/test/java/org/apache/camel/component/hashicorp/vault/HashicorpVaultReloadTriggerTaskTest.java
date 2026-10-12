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

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.camel.CamelContext;
import org.apache.camel.component.hashicorp.vault.vault.HashicorpVaultReloadTriggerTask;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.vault.HashicorpVaultConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Secret refresh against a minimal fake of the HashiCorp Vault KV v2 HTTP API.
 */
class HashicorpVaultReloadTriggerTaskTest extends CamelTestSupport {

    private static HttpServer server;
    // "<engine>/<key>" -> current version
    private static final Map<String, Integer> VERSIONS = new ConcurrentHashMap<>();
    private static final List<String> METADATA_READS = new CopyOnWriteArrayList<>();

    private HashicorpVaultReloadTriggerTask task;

    @BeforeAll
    static void startFakeVault() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/v1/", HashicorpVaultReloadTriggerTaskTest::handle);
        server.start();
    }

    @AfterAll
    static void stopFakeVault() {
        server.stop(0);
    }

    @AfterEach
    void stopTask() {
        if (task != null) {
            task.stop();
        }
        VERSIONS.clear();
        METADATA_READS.clear();
    }

    private static void handle(HttpExchange http) throws IOException {
        // /v1/<engine>/<data|metadata>/<key>[?version=<n>]
        String[] parts = http.getRequestURI().getPath().substring("/v1/".length()).split("/", 3);
        String body = null;
        if (parts.length == 3) {
            Integer version = VERSIONS.get(parts[0] + "/" + parts[2]);
            String query = http.getRequestURI().getQuery();
            if ("metadata".equals(parts[1])) {
                METADATA_READS.add(parts[0] + "/metadata/" + parts[2]);
                if (version != null) {
                    body = "{\"data\":{\"current_version\":" + version + "}}";
                }
            } else if ("data".equals(parts[1]) && version != null) {
                if (query != null && query.startsWith("version=")) {
                    // a specific version was requested
                    version = Integer.valueOf(query.substring("version=".length()));
                }
                body = "{\"data\":{\"data\":{\"password\":\"pw-" + version + "\"},\"metadata\":{\"version\":" + version
                       + "}}}";
            }
        }
        int status = body != null ? 200 : 404;
        byte[] bytes = (body != null ? body : "{\"errors\":[]}").getBytes(StandardCharsets.UTF_8);
        http.getResponseHeaders().add("Content-Type", "application/json");
        http.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = http.getResponseBody()) {
            os.write(bytes);
        }
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext ctx = super.createCamelContext();
        HashicorpVaultConfiguration hashicorp = new HashicorpVaultConfiguration();
        hashicorp.setToken("test-token");
        hashicorp.setHost("localhost");
        hashicorp.setPort(String.valueOf(server.getAddress().getPort()));
        hashicorp.setScheme("http");
        ctx.getVaultConfiguration().setHashicorpVaultConfiguration(hashicorp);
        return ctx;
    }

    private void startTask() {
        task = new HashicorpVaultReloadTriggerTask();
        task.setCamelContext(context);
        task.start();
    }

    @Test
    void rotationInNonDefaultEngineIsDetected() {
        VERSIONS.put("kv/db", 1);
        assertEquals("pw-1", context.resolvePropertyPlaceholders("{{hashicorp:kv:db#password}}"));

        startTask();
        task.run();
        VERSIONS.put("kv/db", 2);
        task.run();

        assertTrue(METADATA_READS.contains("kv/metadata/db"),
                "the refresh task must read the metadata of the engine the secret was resolved from, but read "
                                                              + METADATA_READS);
        assertFalse(task.getUpdates().isEmpty(),
                "the new version of the secret kv:db was not detected, updates: " + task.getUpdates());
    }

    @Test
    void rotationBeforeFirstCheckIsDetected() {
        VERSIONS.put("secret/api", 1);
        assertEquals("pw-1", context.resolvePropertyPlaceholders("{{hashicorp:secret:api#password}}"));

        startTask();
        // the secret is rotated after it was resolved at startup but before the first check
        VERSIONS.put("secret/api", 2);
        task.run();

        assertFalse(task.getUpdates().isEmpty(),
                "the application resolved version 1 but version 2 was not detected, updates: " + task.getUpdates());
    }

    @Test
    void rotationBetweenTwoLookupsBeforeFirstCheckIsDetected() {
        VERSIONS.put("secret/api", 1);
        assertEquals("pw-1", context.resolvePropertyPlaceholders("{{hashicorp:secret:api#password}}"));
        // the secret is rotated, and resolved again (as a dynamic endpoint does) before the first check
        VERSIONS.put("secret/api", 2);
        assertEquals("pw-2", context.resolvePropertyPlaceholders("{{hashicorp:secret:api#password}}"));

        HashicorpVaultPropertiesFunction function
                = (HashicorpVaultPropertiesFunction) context.getPropertiesComponent().getPropertiesFunction("hashicorp");
        assertEquals(1, function.getSecretVersion("api"), "the oldest resolved version is the one to compare with");

        startTask();
        task.run();

        assertFalse(task.getUpdates().isEmpty(),
                "version 1 is still in use by what was resolved first, but version 2 was not detected, updates: "
                                                 + task.getUpdates());
    }

    @Test
    void secretResolvedAtASpecificVersionIsNotComparedWithThatVersion() {
        VERSIONS.put("secret/api", 2);
        // the application asks for version 1, so the version it resolved says nothing about updates of the secret
        assertEquals("pw-1", context.resolvePropertyPlaceholders("{{hashicorp:secret:api#password@1}}"));

        startTask();
        task.run();

        assertTrue(task.getUpdates().isEmpty(), "the first check compared with the requested version, updates: "
                                                + task.getUpdates());
    }

    @Test
    void secretsAreTrackedWithTheirEngine() {
        VERSIONS.put("kv/db", 1);
        VERSIONS.put("secret/api", 1);
        context.resolvePropertyPlaceholders("{{hashicorp:kv:db#password}}");
        context.resolvePropertyPlaceholders("{{hashicorp:secret:api#password}}");

        HashicorpVaultPropertiesFunction function
                = (HashicorpVaultPropertiesFunction) context.getPropertiesComponent().getPropertiesFunction("hashicorp");
        // the format of camel.vault.hashicorp.secrets: a secret of the default secret engine has no engine prefix
        assertEquals(Set.of("kv:db", "api"), function.getSecrets());
    }

    @Test
    void secretWithoutEngineIsTrackedAsDefaultEngine() {
        // a plain hashicorp:api lookup has no engine, so it reads from the default secret engine
        HashicorpVaultPropertiesFunction function = new HashicorpVaultPropertiesFunction();
        function.setCamelContext(context);
        function.start();
        try {
            function.apply("api");

            // tracked like a secret of the default secret engine, which is how the refresh task reads a name
            // without engine, and not as "null:api"
            assertEquals(Set.of("api"), function.getSecrets());
        } finally {
            function.stop();
        }
    }

    @Test
    void unchangedSecretDoesNotTriggerReload() {
        VERSIONS.put("kv/db", 1);
        VERSIONS.put("secret/api", 3);
        context.resolvePropertyPlaceholders("{{hashicorp:kv:db#password}}");
        context.resolvePropertyPlaceholders("{{hashicorp:secret:api#password}}");

        startTask();
        task.run();
        task.run();

        assertTrue(task.getUpdates().isEmpty(), "no secret changed, updates: " + task.getUpdates());
    }
}
