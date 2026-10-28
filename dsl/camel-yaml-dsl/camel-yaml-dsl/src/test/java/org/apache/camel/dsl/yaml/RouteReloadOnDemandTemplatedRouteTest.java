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
import java.util.Set;

import org.apache.camel.ServiceStatus;
import org.apache.camel.dsl.yaml.support.YamlTestSupport;
import org.apache.camel.spi.Resource;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.support.RouteOnDemandReloadStrategy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-25554: a reload that removes all routes creates the routes from templatedRoute entries again, also when the
 * route template is in another file.
 */
class RouteReloadOnDemandTemplatedRouteTest extends YamlTestSupport {

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

    @Override
    public void doSetup() throws Exception {
        dir = Files.createTempDirectory("camel-reload-templated");
        context.start();
    }

    @Override
    public void doCleanup() throws Exception {
        deleteRecursively(dir);
    }

    @Test
    void onDemandReloadCreatesTheTemplatedRoutesAgain() throws Exception {
        Path file = dir.resolve("routes.camel.yaml");
        Files.writeString(file, template() + templatedRoutes("Hello") + plainRoute("plain"));
        loadRoutes(ResourceHelper.resolveResource(context, "file:" + file));
        assertThat(context.getRoutes()).hasSize(3);

        TestableStrategy strategy = new TestableStrategy(dir.toString());
        strategy.setCamelContext(context);
        strategy.setPattern("*.yaml");
        strategy.doStart();
        try {
            Files.writeString(file, template() + templatedRoutes("Hi") + plainRoute("plain changed"));
            strategy.onReload("test");

            assertThat(strategy.getLastError()).isNull();
            assertThat(context.getRoutes()).hasSize(3);
            assertRoute("plain", "direct:plain", "plain changed");
            assertRoute("greet-a", "direct:a", "Hi a");
            assertRoute("greet-b", "direct:b", "Hi b");
        } finally {
            strategy.doStop();
        }
    }

    @Test
    void updateRoutesWithTheTemplateInAFileAfterTheTemplatedRoutes() throws Exception {
        Path uses = dir.resolve("a-uses.camel.yaml");
        Path templates = dir.resolve("b-templates.camel.yaml");
        Files.writeString(uses, templatedRoutes("Hello") + plainRoute("plain"));
        Files.writeString(templates, template());
        Resource usesResource = ResourceHelper.resolveResource(context, "file:" + uses);
        Resource templatesResource = ResourceHelper.resolveResource(context, "file:" + templates);
        loadRoutes(usesResource, templatesResource);
        assertThat(context.getRoutes()).hasSize(3);

        // what a reload that removes all routes does: the file with the templated routes comes first, and its
        // template is only known once the other file is loaded
        context.getRouteController().removeAllRoutes();
        context.removeRouteTemplates("*");
        Set<String> ids = PluginHelper.getRoutesLoader(context).updateRoutes(usesResource, templatesResource);

        assertThat(ids).containsExactlyInAnyOrder("plain", "greet-a", "greet-b");
        assertRoute("plain", "direct:plain", "plain");
        assertRoute("greet-a", "direct:a", "Hello a");
        assertRoute("greet-b", "direct:b", "Hello b");
    }

    private void assertRoute(String routeId, String uri, String expected) {
        assertThat(context.getRouteController().getRouteStatus(routeId)).isEqualTo(ServiceStatus.Started);
        assertThat(context.createFluentProducerTemplate().to(uri).request(String.class)).isEqualTo(expected);
    }

    private static String template() {
        return """
                - routeTemplate:
                    id: "greeting"
                    parameters:
                      - name: "name"
                      - name: "greeting"
                    from:
                      uri: "direct:{{name}}"
                      steps:
                        - setBody:
                            simple: "{{greeting}} {{name}}"
                """;
    }

    private static String templatedRoutes(String greeting) {
        return """
                - templatedRoute:
                    routeTemplateRef: "greeting"
                    routeId: "greet-a"
                    parameters:
                      - name: "name"
                        value: "a"
                      - name: "greeting"
                        value: "%s"
                - templatedRoute:
                    routeTemplateRef: "greeting"
                    routeId: "greet-b"
                    parameters:
                      - name: "name"
                        value: "b"
                      - name: "greeting"
                        value: "%s"
                """.formatted(greeting, greeting);
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
