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
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dsl.yaml.support.YamlTestSupport;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.support.RouteOnDemandReloadStrategy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-25427: an on-demand reload after a route file was removed (or renamed) removes the routes of that file and
 * reloads the others, instead of failing on the missing file and leaving the application without routes.
 */
class RouteReloadOnDemandRemovedFileTest extends YamlTestSupport {

    /** Subclass to expose protected lifecycle methods for testing. */
    static class TestableStrategy extends RouteOnDemandReloadStrategy {
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
    private Path a;
    private Path b;

    @Override
    public void doSetup() throws Exception {
        dir = Files.createTempDirectory("camel-reload-removed");
        a = dir.resolve("a.camel.yaml");
        b = dir.resolve("b.camel.yaml");
        Files.writeString(a, route("a", "mock:a"));
        Files.writeString(b, route("b", "mock:b"));
        context.start();
        loadRoutes(ResourceHelper.resolveResource(context, "file:" + a),
                ResourceHelper.resolveResource(context, "file:" + b));
    }

    @Override
    public void doCleanup() throws Exception {
        deleteRecursively(dir);
    }

    @Test
    void aRemovedFileRemovesItsRoutesAndTheOthersReload() throws Exception {
        TestableStrategy strategy = newStrategy();
        try {
            assertThat(context.getRoutes()).hasSize(2);

            Files.delete(b);
            strategy.onReload("test");

            assertOnlyRouteARuns(strategy);

            // a later reload works too
            strategy.onReload("test");
            assertOnlyRouteARuns(strategy);
        } finally {
            strategy.doStop();
        }
    }

    @Test
    void aFileRestoredFromMemoryCanBeRemoved() throws Exception {
        TestableStrategy strategy = newStrategy();
        try {
            // one successful reload, so the content that runs is remembered
            strategy.onReload("test");
            assertThat(strategy.getFailedCounter()).isZero();

            // b is saved with a mistake (pollEnrich takes an expression, not a uri): the reload fails and the routes
            // are restored from the content that last loaded
            Files.writeString(b, """
                    - route:
                        id: b
                        from:
                          uri: direct:b
                          steps:
                            - pollEnrich:
                                uri: file:./order.json
                    """);
            strategy.onReload("test");
            assertThat(strategy.getFailedCounter()).isEqualTo(1);
            assertThat(context.getRouteController().getRouteStatus("b")).isEqualTo(ServiceStatus.Started);

            // the broken file is removed instead of fixed: route b runs from memory, but its file is gone
            Files.delete(b);
            strategy.onReload("test");

            assertThat(strategy.getFailedCounter()).isEqualTo(1);
            assertOnlyRouteARuns(strategy);
        } finally {
            strategy.doStop();
        }
    }

    private TestableStrategy newStrategy() throws Exception {
        TestableStrategy strategy = new TestableStrategy(dir.toString());
        strategy.setCamelContext(context);
        strategy.setPattern("*.yaml");
        strategy.doStart();
        return strategy;
    }

    private void assertOnlyRouteARuns(TestableStrategy strategy) throws Exception {
        assertThat(strategy.getLastError()).isNull();
        assertThat(context.getRoutes()).hasSize(1);
        assertThat(context.getRouteController().getRouteStatus("a")).isEqualTo(ServiceStatus.Started);
        assertThat(context.getRoute("b")).isNull();

        MockEndpoint mock = context.getEndpoint("mock:a", MockEndpoint.class);
        mock.reset();
        mock.expectedMessageCount(1);
        context.createProducerTemplate().sendBody("direct:a", "x");
        mock.assertIsSatisfied();
    }

    private static String route(String id, String to) {
        return """
                - route:
                    id: %s
                    from:
                      uri: direct:%s
                      steps:
                        - to:
                            uri: %s
                """.formatted(id, id, to);
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
