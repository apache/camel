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
import java.util.Collection;

import org.apache.camel.ServiceStatus;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dsl.yaml.support.YamlTestSupport;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.support.RouteWatcherReloadStrategy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-24860: a reload that fails (a route file saved with a mistake) restores the routes that ran before, instead of
 * leaving the application without routes until the next successful save. CAMEL-24899: a project whose routes are all in
 * one file goes back to the content that last loaded, which is kept in memory.
 * <p>
 * The mistake the tests save is always the same one, and it is a real one (CAMEL-24850): the endpoint of pollEnrich is
 * an expression, so {@code pollEnrich: {uri: "file:./order.json"}} has no uri property to bind and the loader rejects
 * the file with "pollEnrich: unsupported field: uri". It is the right kind of mistake here because it fails while the
 * routes are built, not while the YAML is parsed, which is what a reload has to survive. The form that works is
 * {@code pollEnrich: {expression: {constant: {expression: "file:./order.json"}}}}.
 */
class RouteReloadRollbackTest extends YamlTestSupport {

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

        @Override
        public void onRouteReload(Collection<org.apache.camel.spi.Resource> resources, boolean removeEverything) {
            super.onRouteReload(resources, removeEverything);
        }
    }

    private Path dir;
    private Path good;
    private Path bad;

    @Override
    public void doSetup() throws Exception {
        dir = Files.createTempDirectory("camel-reload");
        good = dir.resolve("good.camel.yaml");
        bad = dir.resolve("bad.camel.yaml");
        Files.writeString(good, """
                - route:
                    id: good
                    from:
                      uri: direct:good
                      steps:
                        - to:
                            uri: mock:good
                """);
        Files.writeString(bad, """
                - route:
                    id: bad
                    from:
                      uri: direct:bad
                      steps:
                        - to:
                            uri: mock:bad
                """);
        context.start();
        loadRoutes(ResourceHelper.resolveResource(context, "file:" + good),
                ResourceHelper.resolveResource(context, "file:" + bad));
    }

    @Override
    public void doCleanup() throws Exception {
        deleteRecursively(dir);
    }

    @Test
    void aFailedReloadRestoresThePreviousRoutes() throws Exception {
        TestableStrategy strategy = new TestableStrategy(dir.toString());
        strategy.setCamelContext(context);
        strategy.setPattern("*.yaml");
        // the strategy is not started (no file watcher in the test): its reload callback is driven by hand
        strategy.doStart();
        try {
            assertThat(context.getRouteController().getRouteStatus("good")).isEqualTo(ServiceStatus.Started);
            assertThat(context.getRouteController().getRouteStatus("bad")).isEqualTo(ServiceStatus.Started);

            // the second file is saved with a mistake (pollEnrich takes an expression, not a uri)
            Files.writeString(bad, """
                    - route:
                        id: bad
                        from:
                          uri: direct:bad
                          steps:
                            - pollEnrich:
                                uri: file:./order.json
                    """);
            Exception failure = null;
            try {
                strategy.getResourceReload().onReload(bad.toString(),
                        ResourceHelper.resolveResource(context, "file:" + bad));
            } catch (Exception e) {
                failure = e;
            }

            // the reload fails, and the route of the other file runs again
            assertThat(failure).isNotNull();
            assertThat(context.getRouteController().getRouteStatus("good")).isEqualTo(ServiceStatus.Started);
            assertThat(context.getRoute("bad")).isNull();

            // the file is fixed
            Files.writeString(bad, """
                    - route:
                        id: bad
                        from:
                          uri: direct:bad
                          steps:
                            - to:
                                uri: mock:bad
                    """);
            strategy.getResourceReload().onReload(bad.toString(),
                    ResourceHelper.resolveResource(context, "file:" + bad));

            // both run
            assertThat(context.getRouteController().getRouteStatus("good")).isEqualTo(ServiceStatus.Started);
            assertThat(context.getRouteController().getRouteStatus("bad")).isEqualTo(ServiceStatus.Started);
        } finally {
            strategy.doStop();
        }
    }

    @Test
    void theOnlyRouteFileKeepsItsPreviousVersionWhenTheSaveIsBroken() throws Exception {
        Path solo = Files.createTempDirectory("camel-reload-solo");
        Path only = solo.resolve("only.camel.yaml");
        Files.writeString(only, """
                - route:
                    id: only
                    from:
                      uri: direct:only
                      steps:
                        - to:
                            uri: mock:only
                """);
        DefaultCamelContext context2 = new DefaultCamelContext();
        context2.disableJMX();
        context2.start();
        try {
            PluginHelper.getRoutesLoader(context2)
                    .loadRoutes(ResourceHelper.resolveResource(context2, "file:" + only));
            context2.startAllRoutes();
            TestableStrategy strategy = new TestableStrategy(solo.toString());
            strategy.setCamelContext(context2);
            strategy.setPattern("*.yaml");
            strategy.doStart();
            try {
                // one successful reload, so the content that runs is remembered
                strategy.getResourceReload().onReload(only.toString(),
                        ResourceHelper.resolveResource(context2, "file:" + only));
                assertThat(context2.getRouteController().getRouteStatus("only")).isEqualTo(ServiceStatus.Started);

                // the only route file is saved with a mistake (pollEnrich takes an expression, not a uri)
                Files.writeString(only, """
                        - route:
                            id: only
                            from:
                              uri: direct:only
                              steps:
                                - pollEnrich:
                                    uri: file:./order.json
                        """);
                Exception failure = null;
                try {
                    strategy.getResourceReload().onReload(only.toString(),
                            ResourceHelper.resolveResource(context2, "file:" + only));
                } catch (Exception e) {
                    failure = e;
                }

                // the reload fails and the version that ran before is still running
                assertThat(failure).isNotNull();
                assertThat(context2.getRouteController().getRouteStatus("only")).isEqualTo(ServiceStatus.Started);

                // the file is fixed
                Files.writeString(only, """
                        - route:
                            id: only
                            from:
                              uri: direct:only
                              steps:
                                - to:
                                    uri: mock:fixed
                        """);
                strategy.getResourceReload().onReload(only.toString(),
                        ResourceHelper.resolveResource(context2, "file:" + only));

                // the fixed version runs, not the remembered one: the message lands in mock:fixed
                assertThat(context2.getRouteController().getRouteStatus("only")).isEqualTo(ServiceStatus.Started);
                assertThat(context2.getRoutes().size()).isEqualTo(1);
                MockEndpoint fixed = context2.getEndpoint("mock:fixed", MockEndpoint.class);
                fixed.expectedMessageCount(1);
                MockEndpoint stale = context2.getEndpoint("mock:only", MockEndpoint.class);
                stale.expectedMessageCount(0);
                context2.createProducerTemplate().sendBody("direct:only", "x");
                MockEndpoint.assertIsSatisfied(context2);
            } finally {
                strategy.doStop();
            }
        } finally {
            context2.stop();
            deleteRecursively(solo);
        }
    }

    @Test
    void everythingRemovedForgetsTheRememberedContentADeletedFileIsNotPutBack() throws Exception {
        Path gone = Files.createTempDirectory("camel-reload-gone");
        Path file = gone.resolve("gone.camel.yaml");
        Files.writeString(file, """
                - route:
                    id: gone
                    from:
                      uri: direct:gone
                      steps:
                        - to:
                            uri: mock:gone
                """);
        DefaultCamelContext ctx = new DefaultCamelContext();
        ctx.disableJMX();
        ctx.start();
        try {
            TestableStrategy strategy = new TestableStrategy(gone.toString());
            strategy.setCamelContext(ctx);
            strategy.setPattern("*.yaml");
            strategy.doStart();
            try {
                strategy.getResourceReload().onReload(file.toString(),
                        ResourceHelper.resolveResource(ctx, "file:" + file));
                assertThat(ctx.getRouteController().getRouteStatus("gone")).isEqualTo(ServiceStatus.Started);

                // every route file is removed (the on-demand strategy asks for that when the directory is empty)
                strategy.onRouteReload(null, true);

                // no routes run
                assertThat(ctx.getRoutes().isEmpty()).isTrue();

                // the file comes back with a mistake (pollEnrich takes an expression, not a uri)
                Files.writeString(file, """
                        - route:
                            id: gone
                            from:
                              uri: direct:gone
                              steps:
                                - pollEnrich:
                                    uri: file:./order.json
                        """);
                Exception failure = null;
                try {
                    strategy.getResourceReload().onReload(file.toString(),
                            ResourceHelper.resolveResource(ctx, "file:" + file));
                } catch (Exception e) {
                    failure = e;
                }

                // the reload fails and the removed route is not put back from memory
                assertThat(failure).isNotNull();
                assertThat(ctx.getRoutes().isEmpty()).isTrue();
            } finally {
                strategy.doStop();
            }
        } finally {
            ctx.stop();
            deleteRecursively(gone);
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
