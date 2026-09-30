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
package org.apache.camel.dsl.yaml;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import org.apache.camel.ServiceStatus;
import org.apache.camel.dsl.yaml.support.YamlTestSupport;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.support.RouteWatcherReloadStrategy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-24866: a new route file next to a file with several routes reloads without a duplicate route id: the existing
 * file is one source, whatever the number of routes it holds.
 */
class RouteReloadNewFileTest extends YamlTestSupport {

    /** Subclass to expose protected lifecycle methods for testing without a live file watcher. */
    static class TestableStrategy extends RouteWatcherReloadStrategy {
        TestableStrategy(String dir) {
            super(dir);
        }

        @Override
        public void doStart() throws Exception {
            super.doStart();
        }

        @Override
        public void doStop() throws Exception {
            super.doStop();
        }
    }

    private Path dir;
    private Path shop;

    @Override
    public void doSetup() throws Exception {
        dir = Files.createTempDirectory("camel-reload");
        shop = dir.resolve("shop.camel.yaml");
        Files.writeString(shop, """
                - route:
                    id: orders
                    from:
                      uri: direct:orders
                      steps:
                        - to:
                            uri: mock:orders
                - route:
                    id: report
                    from:
                      uri: direct:report
                      steps:
                        - to:
                            uri: mock:report
                - route:
                    id: setup
                    from:
                      uri: direct:setup
                      steps:
                        - to:
                            uri: mock:setup
                """);
        context.start();
        loadRoutes(ResourceHelper.resolveResource(context, "file:" + shop));
    }

    @Override
    public void doCleanup() throws Exception {
        deleteRecursively(dir);
    }

    @Test
    void aNewFileReloadsNextToAFileWithSeveralRoutes() throws Exception {
        TestableStrategy strategy = new TestableStrategy(dir.toString());
        strategy.setCamelContext(context);
        strategy.setPattern("*.yaml");
        // the strategy is not started (no file watcher in the test): its reload callback is driven by hand
        strategy.doStart();
        try {
            assertThat(context.getRoutes().size()).isEqualTo(3);

            // a second file is added
            Path hello = dir.resolve("hello.camel.yaml");
            Files.writeString(hello, """
                    - route:
                        id: hello
                        from:
                          uri: direct:hello
                          steps:
                            - to:
                                uri: mock:hello
                    """);
            strategy.getResourceReload().onReload(hello.toString(),
                    ResourceHelper.resolveResource(context, "file:" + hello));

            // the three routes of the first file and the new one run
            assertThat(context.getRoutes().size()).isEqualTo(4);
            for (String id : new String[] { "orders", "report", "setup", "hello" }) {
                assertThat(context.getRouteController().getRouteStatus(id))
                        .as("Route " + id + " should be started")
                        .isEqualTo(ServiceStatus.Started);
            }
        } finally {
            strategy.doStop();
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return;
        }
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
