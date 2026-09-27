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
import java.util.Properties;

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
 * CAMEL-25032: a reload that failed because a property was missing is retried when the properties change. Dev mode
 * reloads one file at a time, so a route saved with a property that is added in the same edit fails to start; before
 * this the failed file only loaded again on its next save, so the application kept running the previous route even
 * though the property was there.
 */
class RouteReloadPropertiesRetryTest extends YamlTestSupport {

    /** Subclass to drive the reload callbacks by hand, without a live file watcher. */
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
        public void onRouteReload(Collection<Resource> resources, boolean removeEverything) {
            super.onRouteReload(resources, removeEverything);
        }
    }

    /** The service onPropertiesReload needs to see: dev mode registers one, and only then are properties reloaded. */
    static class NoopPropertiesReload extends ServiceSupport implements PropertiesReload {
        @Override
        public void onReload(String name, Properties properties) {
        }
    }

    private Path dir;
    private Path route;
    private Path props;

    @Override
    public void doSetup() throws Exception {
        dir = Files.createTempDirectory("camel-reload-props");
        route = dir.resolve("shop.camel.yaml");
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
        context.getPropertiesComponent().setLocation("file:" + props);
        context.addService(new NoopPropertiesReload());
        context.start();
        loadRoutes(ResourceHelper.resolveResource(context, "file:" + route));
    }

    @Override
    public void doCleanup() throws Exception {
        deleteRecursively(dir);
    }

    @Test
    void aReloadThatFailedOnAMissingPropertyLoadsWhenThePropertyArrives() throws Exception {
        TestableStrategy strategy = new TestableStrategy(dir.toString());
        strategy.setCamelContext(context);
        strategy.setPattern("*.yaml,*.properties");
        strategy.doStart();
        try {
            // one successful reload, so the content that runs is remembered
            strategy.getResourceReload().onReload(route.toString(),
                    ResourceHelper.resolveResource(context, "file:" + route));
            assertThat(context.getRouteController().getRouteStatus("shop")).isEqualTo(ServiceStatus.Started);

            // the route is saved using a property that is not in application.properties yet, which is what an edit
            // that adds both looks like when dev mode reloads the route file first
            Files.writeString(route, """
                    - route:
                        id: shop
                        from:
                          uri: direct:shop
                          steps:
                            - to:
                                uri: "mock:{{shop.currency}}"
                    """);
            Exception failure = null;
            try {
                strategy.getResourceReload().onReload(route.toString(),
                        ResourceHelper.resolveResource(context, "file:" + route));
            } catch (Exception e) {
                failure = e;
            }

            // it fails, and the version that ran is restored: the new endpoint is nowhere
            assertThat(failure).isNotNull();
            assertThat(context.getRouteController().getRouteStatus("shop")).isEqualTo(ServiceStatus.Started);
            assertThat(context.hasEndpoint("mock://EUR")).isNull();

            // the property arrives
            Files.writeString(props, "shop.name=Camel Shop\nshop.currency=EUR\n");
            strategy.getResourceReload().onReload(props.toString(),
                    ResourceHelper.resolveResource(context, "file:" + props));

            // the file that failed is loaded from disk again, so the route that runs is the saved one
            assertThat(context.getRouteController().getRouteStatus("shop")).isEqualTo(ServiceStatus.Started);
            assertThat(context.hasEndpoint("mock://EUR")).isNotNull();
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
