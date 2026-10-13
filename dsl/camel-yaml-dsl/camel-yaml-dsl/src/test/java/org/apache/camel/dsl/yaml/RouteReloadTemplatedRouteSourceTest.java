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

import org.apache.camel.Route;
import org.apache.camel.ServiceStatus;
import org.apache.camel.dsl.yaml.support.YamlTestSupport;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.support.RouteWatcherReloadStrategy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-25598: a route created from a templated route records the file with its route template (as its source) and the
 * file with the templated route, so a reload of another file loads both again.
 */
class RouteReloadTemplatedRouteSourceTest extends YamlTestSupport {

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
    private Path templates;
    private Path uses;
    private Path plain;

    @Override
    public void doSetup() throws Exception {
        dir = Files.createTempDirectory("camel-reload-templated-source");
        templates = dir.resolve("templates.camel.yaml");
        uses = dir.resolve("uses.camel.yaml");
        plain = dir.resolve("plain.camel.yaml");
        Files.writeString(templates, """
                - routeTemplate:
                    id: "greeting"
                    parameters:
                      - name: "greeting"
                    from:
                      uri: "direct:greet"
                      steps:
                        - setBody:
                            constant: "{{greeting}}"
                """);
        Files.writeString(uses, """
                - templatedRoute:
                    routeTemplateRef: "greeting"
                    routeId: "greet"
                    parameters:
                      - name: "greeting"
                        value: "Hello"
                """);
        Files.writeString(plain, plainRoute("plain"));
        context.start();
        loadRoutes(ResourceHelper.resolveResource(context, "file:" + templates),
                ResourceHelper.resolveResource(context, "file:" + uses),
                ResourceHelper.resolveResource(context, "file:" + plain));
    }

    @Override
    public void doCleanup() throws Exception {
        deleteRecursively(dir);
    }

    @Test
    void routeRecordsItsSources() {
        Route greet = context.getRoute("greet");
        assertThat(greet.getRouteTemplateId()).isEqualTo("greeting");
        assertThat(greet.getSourceResource().getLocation()).isEqualTo("file:" + templates);
        assertThat(greet.getSourceLocation()).startsWith("file:" + templates + ":");
        assertThat(greet.getTemplatedRouteResource().getLocation()).isEqualTo("file:" + uses);

        Route other = context.getRoute("plain");
        assertThat(other.getRouteTemplateId()).isNull();
        assertThat(other.getSourceResource().getLocation()).isEqualTo("file:" + plain);
        assertThat(other.getTemplatedRouteResource()).isNull();
    }

    @Test
    void reloadOfAnotherFileKeepsTheTemplatedRoute() throws Exception {
        TestableStrategy strategy = new TestableStrategy(dir.toString());
        strategy.setCamelContext(context);
        strategy.setPattern("*.yaml");
        // the strategy is not started (no file watcher in the test): its reload callback is driven by hand
        strategy.doStart();
        try {
            Files.writeString(plain, plainRoute("plain changed"));
            strategy.getResourceReload().onReload(plain.toString(),
                    ResourceHelper.resolveResource(context, "file:" + plain));

            assertThat(strategy.getFailedCounter()).isZero();
            assertThat(strategy.getLastError()).isNull();
            assertThat(context.getRoutes()).hasSize(2);
            assertThat(context.getRouteController().getRouteStatus("greet")).isEqualTo(ServiceStatus.Started);
            assertThat(context.createFluentProducerTemplate().to("direct:greet").request(String.class))
                    .isEqualTo("Hello");
            assertThat(context.createFluentProducerTemplate().to("direct:plain").request(String.class))
                    .isEqualTo("plain changed");
            assertThat(context.getRoute("greet").getTemplatedRouteResource().getLocation()).isEqualTo("file:" + uses);
        } finally {
            strategy.doStop();
        }
    }

    private static String plainRoute(String body) {
        return """
                - route:
                    id: plain
                    from:
                      uri: direct:plain
                      steps:
                        - setBody:
                            constant: "%s"
                """.formatted(body);
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
