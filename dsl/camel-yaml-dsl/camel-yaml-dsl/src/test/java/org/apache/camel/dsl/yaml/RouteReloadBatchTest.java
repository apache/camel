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

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.ServiceStatus;
import org.apache.camel.dsl.yaml.support.YamlTestSupport;
import org.apache.camel.spi.PropertiesReload;
import org.apache.camel.spi.Resource;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.support.RouteWatcherReloadStrategy;
import org.apache.camel.support.service.ServiceSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-25032: one save of several files is reloaded as one change. The properties of the change are applied first,
 * each without reloading the routes, and the routes are then reloaded once - so a route saved together with a property
 * it uses is built with that property in place and does not fail at all.
 */
class RouteReloadBatchTest extends YamlTestSupport {

    /** Subclass to drive the batch callback by hand, without a live file watcher, and to count the route reloads. */
    static class TestableStrategy extends RouteWatcherReloadStrategy {
        final AtomicInteger routeReloads = new AtomicInteger();

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

        @Override
        public void onReloadBatch(List<File> changed) {
            super.onReloadBatch(changed);
        }

        @Override
        protected void onRouteReload(Collection<Resource> resources, boolean removeEverything) {
            routeReloads.incrementAndGet();
            super.onRouteReload(resources, removeEverything);
        }
    }

    static class NoopPropertiesReload extends ServiceSupport implements PropertiesReload {
        @Override
        public void onReload(String name, Properties properties) {
        }
    }

    private Path dir;
    private Path route;
    private Path other;
    private Path props;

    @Override
    public void doSetup() throws Exception {
        dir = Files.createTempDirectory("camel-reload-batch");
        route = dir.resolve("shop.camel.yaml");
        other = dir.resolve("other.camel.yaml");
        props = dir.resolve("application.properties");
        Files.writeString(props, "shop.name=Camel Shop\n");
        Files.writeString(route, """
                - route:
                    id: shop
                    from:
                      uri: direct:shop
                      steps:
                        - to:
                            uri: mock:started
                """);
        Files.writeString(other, """
                - route:
                    id: other
                    from:
                      uri: direct:other
                      steps:
                        - to:
                            uri: mock:other
                """);
        context.getPropertiesComponent().setLocation("file:" + props);
        context.addService(new NoopPropertiesReload());
        context.start();
        loadRoutes(ResourceHelper.resolveResource(context, "file:" + route),
                ResourceHelper.resolveResource(context, "file:" + other));
    }

    @Override
    public void doCleanup() throws Exception {
        deleteRecursively(dir);
    }

    @Test
    void aRouteAndThePropertyItUsesSavedTogetherAreOneReloadThatDoesNotFail() throws Exception {
        TestableStrategy strategy = new TestableStrategy(dir.toString());
        strategy.setCamelContext(context);
        strategy.setPattern("*.yaml,*.properties");
        strategy.doStart();
        try {
            // the save: one route starts using a property, the property is added, and a second route file changes too
            Files.writeString(route, """
                    - route:
                        id: shop
                        from:
                          uri: direct:shop
                          steps:
                            - to:
                                uri: "mock:{{shop.currency}}"
                    """);
            Files.writeString(other, """
                    - route:
                        id: other
                        from:
                          uri: direct:other
                          steps:
                            - to:
                                uri: mock:other-v2
                    """);
            Files.writeString(props, "shop.name=Camel Shop\nshop.currency=EUR\n");

            // the route files first, as the watch service may well report them first
            strategy.onReloadBatch(List.of(route.toFile(), other.toFile(), props.toFile()));

            // one reload for the whole save, not one per file: the properties are applied first, without reloading the
            // routes for each, so the route that uses the new property is built once with it already in place
            assertThat(strategy.routeReloads).hasValue(1);
            assertThat(strategy.getLastError()).isNull();
            assertThat(strategy.getFailedCounter()).isZero();
            assertThat(context.getRouteController().getRouteStatus("shop")).isEqualTo(ServiceStatus.Started);
            assertThat(context.getRouteController().getRouteStatus("other")).isEqualTo(ServiceStatus.Started);
            assertThat(context.hasEndpoint("mock://EUR")).isNotNull();
            assertThat(context.hasEndpoint("mock://other-v2")).isNotNull();
        } finally {
            strategy.doStop();
        }
    }

    @Test
    void aBatchWithOneBrokenFileStillLoadsTheOthers() throws Exception {
        TestableStrategy strategy = new TestableStrategy(dir.toString());
        strategy.setCamelContext(context);
        strategy.setPattern("*.yaml,*.properties");
        strategy.doStart();
        try {
            // one of the two saved files has the mistake of CAMEL-24850: pollEnrich takes an expression, not a uri
            Files.writeString(route, """
                    - route:
                        id: shop
                        from:
                          uri: direct:shop
                          steps:
                            - pollEnrich:
                                uri: file:./order.json
                    """);
            Files.writeString(other, """
                    - route:
                        id: other
                        from:
                          uri: direct:other
                          steps:
                            - to:
                                uri: mock:other-v2
                    """);

            // the batch fails, so the files are reloaded one at a time and the good one gets through
            strategy.onReloadBatch(List.of(route.toFile(), other.toFile()));

            assertThat(context.getRouteController().getRouteStatus("other")).isEqualTo(ServiceStatus.Started);
            assertThat(context.hasEndpoint("mock://other-v2")).isNotNull();
            assertThat(context.getRoute("shop")).isNull();
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
