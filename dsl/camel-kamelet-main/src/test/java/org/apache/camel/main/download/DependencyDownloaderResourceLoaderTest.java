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
package org.apache.camel.main.download;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;
import org.apache.camel.impl.engine.SimpleCamelContext;
import org.apache.camel.spi.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CAMEL-24852: a classpath: or file: resource not found is looked up in the directories of the route files. */
public class DependencyDownloaderResourceLoaderTest {

    @TempDir
    Path routes;

    @Test
    void aScriptNextToTheRouteIsFoundByClasspathName() throws Exception {
        Files.writeString(routes.resolve("mapping.groovy"), "body");
        SimpleCamelContext context = new SimpleCamelContext();
        DependencyDownloaderResourceLoader loader
                = new DependencyDownloaderResourceLoader(context, null, List.of(routes.toString()));

        Resource resource = loader.resolveResource("classpath:mapping.groovy");
        assertTrue(resource.exists());
        assertEquals("body", new String(resource.getInputStream().readAllBytes()));

        resource = loader.resolveResource("file:mapping.groovy");
        assertTrue(resource.exists(), "a file: reference relative to the route directory");
    }

    @Test
    void aResourceThatIsNowhereKeepsItsName() {
        SimpleCamelContext context = new SimpleCamelContext();
        DependencyDownloaderResourceLoader loader
                = new DependencyDownloaderResourceLoader(context, null, List.of(routes.toString()));

        Resource resource = loader.resolveResource("classpath:missing.groovy");
        assertFalse(resource.exists());
        assertEquals("classpath:missing.groovy", resource.getLocation(), "the error names the resource as written");
    }

    /** CAMEL-24865: under --source-dir a resource that exists nowhere keeps its original (not found) answer. */
    @Test
    void aMissingResourceUnderSourceDirIsStillMissing() throws Exception {
        Path sourceDir = Files.createDirectory(routes.resolve("src"));
        SimpleCamelContext context = new SimpleCamelContext();
        DependencyDownloaderResourceLoader loader
                = new DependencyDownloaderResourceLoader(context, sourceDir.toString(), List.of());

        Resource resource = loader.resolveResource("classpath:camel-joor.properties");
        assertFalse(resource.exists());
        assertEquals("classpath:camel-joor.properties", resource.getLocation(),
                "not replaced by a file in the source dir that does not exist");

        // the form of the bug: with ?optional=true it is still optional (JavaLanguage failed on it)
        Resource optional = loader.resolveResource("classpath:camel-joor.properties?optional=true");
        assertFalse(optional.exists());
        assertEquals("classpath:camel-joor.properties?optional=true", optional.getLocation(),
                "?optional=true resource must not be replaced by a non-existent file");
    }

    @Test
    void theSourceDirWinsWhenSet() throws Exception {
        Path sourceDir = Files.createDirectory(routes.resolve("src"));
        Files.writeString(sourceDir.resolve("mapping.groovy"), "from source dir");
        Files.writeString(routes.resolve("mapping.groovy"), "next to the route");
        SimpleCamelContext context = new SimpleCamelContext();
        DependencyDownloaderResourceLoader loader
                = new DependencyDownloaderResourceLoader(context, sourceDir.toString(), List.of(routes.toString()));

        Resource resource = loader.resolveResource("classpath:mapping.groovy");
        assertEquals("from source dir", new String(resource.getInputStream().readAllBytes()));
    }

    /** Resolving an http: resource must not fetch it: only classpath: and file: resources are probed and looked up. */
    @Test
    void anHttpResourceIsNotFetchedWhenResolved() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/openapi.json", exchange -> {
            requests.incrementAndGet();
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        try {
            SimpleCamelContext context = new SimpleCamelContext();
            DependencyDownloaderResourceLoader loader
                    = new DependencyDownloaderResourceLoader(context, null, List.of(routes.toString()));

            Resource resource
                    = loader.resolveResource("http://localhost:" + server.getAddress().getPort() + "/openapi.json");
            assertEquals(0, requests.get(), "resolving fetched the resource (rest-openapi then read its specification twice)");
            assertEquals("{}", new String(resource.getInputStream().readAllBytes()));
            assertEquals(1, requests.get());
        } finally {
            server.stop(0);
        }
    }
}
