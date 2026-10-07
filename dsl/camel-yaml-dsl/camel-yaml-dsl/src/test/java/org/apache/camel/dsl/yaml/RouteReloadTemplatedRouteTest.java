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
 * CAMEL-25068: a route from a template fails when a route with its id exists. Reloading a file with a templated route
 * (keeping the other routes) must still work.
 */
class RouteReloadTemplatedRouteTest extends YamlTestSupport {

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
    private Path file;

    @Override
    public void doSetup() throws Exception {
        dir = Files.createTempDirectory("camel-reload");
        file = dir.resolve("greet.camel.yaml");
        Files.writeString(file, routes("Hello"));
        context.start();
        loadRoutes(ResourceHelper.resolveResource(context, "file:" + file));
    }

    @Override
    public void doCleanup() throws Exception {
        deleteRecursively(dir);
    }

    @Test
    void reloadKeepingTheOtherRoutes() throws Exception {
        TestableStrategy strategy = new TestableStrategy(dir.toString());
        strategy.setCamelContext(context);
        strategy.setPattern("*.yaml");
        strategy.setRemoveAllRoutes(false);
        // the strategy is not started (no file watcher in the test): its reload callback is driven by hand
        strategy.doStart();
        try {
            assertThat(context.getRouteController().getRouteStatus("greet")).isEqualTo(ServiceStatus.Started);

            Files.writeString(file, routes("Hi"));
            strategy.getResourceReload().onReload(file.toString(),
                    ResourceHelper.resolveResource(context, "file:" + file));

            // the reload does not fail on the id of the templated route, and both routes run
            assertThat(context.getRouteController().getRouteStatus("greet")).isEqualTo(ServiceStatus.Started);
            assertThat(context.getRouteController().getRouteStatus("plain")).isEqualTo(ServiceStatus.Started);
            assertThat(context.createFluentProducerTemplate().to("direct:plain").request(String.class))
                    .isEqualTo("plain Hi");
        } finally {
            strategy.doStop();
        }
    }

    private static String routes(String greeting) {
        return """
                - routeTemplate:
                    id: "greeting"
                    parameters:
                      - name: "greeting"
                    from:
                      uri: "direct:greet"
                      steps:
                        - setBody:
                            constant: "{{greeting}}"
                - templatedRoute:
                    routeTemplateRef: "greeting"
                    routeId: "greet"
                    parameters:
                      - name: "greeting"
                        value: "%s"
                - route:
                    id: plain
                    from:
                      uri: direct:plain
                      steps:
                        - setBody:
                            constant: "plain %s"
                """.formatted(greeting, greeting);
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
