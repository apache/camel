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
 * CAMEL-25619: a route template in a watched file that a route uses only through {@code kamelet:} (no templated route)
 * is loaded again on a reload, before the route that uses it.
 */
class RouteReloadKameletTemplateFileTest extends YamlTestSupport {

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
    private TestableStrategy strategy;

    @Override
    public void doSetup() throws Exception {
        dir = Files.createTempDirectory("camel-reload-kamelet-template");
        templates = dir.resolve("templates.camel.yaml");
        uses = dir.resolve("uses.camel.yaml");
        plain = dir.resolve("plain.camel.yaml");
        Files.writeString(templates, template("{{greeting}}"));
        Files.writeString(uses, """
                - route:
                    id: caller
                    from:
                      uri: direct:start
                      steps:
                        - to: "kamelet:greeting?greeting=Hello"
                """);
        Files.writeString(plain, plainRoute("plain"));
        context.start();
        loadRoutes(ResourceHelper.resolveResource(context, "file:" + templates),
                ResourceHelper.resolveResource(context, "file:" + uses),
                ResourceHelper.resolveResource(context, "file:" + plain));
        assertThat(greet()).isEqualTo("Hello");

        strategy = new TestableStrategy(dir.toString());
        strategy.setCamelContext(context);
        strategy.setPattern("*.yaml");
        // the strategy is not started (no file watcher in the test): its reload callback is driven by hand
        strategy.doStart();
    }

    @Override
    public void doCleanup() throws Exception {
        if (strategy != null) {
            strategy.doStop();
        }
        deleteRecursively(dir);
    }

    @Test
    void reloadOfAnotherFileKeepsTheRouteUsingTheKamelet() throws Exception {
        Files.writeString(plain, plainRoute("plain changed"));
        reload(plain);

        assertThat(strategy.getFailedCounter()).isZero();
        assertThat(strategy.getLastError()).isNull();
        assertThat(context.getRouteController().getRouteStatus("caller")).isEqualTo(ServiceStatus.Started);
        assertThat(greet()).isEqualTo("Hello");
        assertThat(context.createFluentProducerTemplate().to("direct:plain").request(String.class))
                .isEqualTo("plain changed");
    }

    @Test
    void reloadOfTheRouteTemplateFileIsUsedByTheRouteUsingTheKamelet() throws Exception {
        // the file with the route template is loaded before the route that uses it, also when it is the changed file
        Files.writeString(templates, template("{{greeting}} World"));
        reload(templates);

        assertThat(strategy.getFailedCounter()).isZero();
        assertThat(strategy.getLastError()).isNull();
        assertThat(greet()).isEqualTo("Hello World");
    }

    @Test
    void failedReloadRestoresTheRouteUsingTheKamelet() throws Exception {
        Files.writeString(plain, """
                - route:
                    id: plain
                    from:
                      uri: direct:plain
                      steps:
                        - noSuchEip: {}
                """);
        Exception failure = null;
        try {
            reload(plain);
        } catch (Exception e) {
            failure = e;
        }

        // the reload fails and the routes that ran before are restored (CAMEL-24860), the route using the Kamelet too
        assertThat(failure).isNotNull();
        assertThat(context.getRouteController().getRouteStatus("caller")).isEqualTo(ServiceStatus.Started);
        assertThat(greet()).isEqualTo("Hello");
        assertThat(context.createFluentProducerTemplate().to("direct:plain").request(String.class)).isEqualTo("plain");
    }

    private void reload(Path file) throws Exception {
        strategy.getResourceReload().onReload(file.toString(), ResourceHelper.resolveResource(context, "file:" + file));
    }

    private String greet() {
        return context.createFluentProducerTemplate().to("direct:start").request(String.class);
    }

    private static String template(String body) {
        return """
                - routeTemplate:
                    id: "greeting"
                    parameters:
                      - name: "greeting"
                    from:
                      uri: "kamelet:source"
                      steps:
                        - setBody:
                            simple: "%s"
                """.formatted(body);
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
