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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.camel.dsl.yaml.support.YamlTestSupport;
import org.apache.camel.model.BeanFactoryDefinition;
import org.apache.camel.model.Model;
import org.apache.camel.spi.Resource;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.support.RouteWatcherReloadStrategy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-25428: when a Java class changes, the beans of a beans file are created again, so the routes call the new code:
 * both a bean of the changed class and a bean that calls it.
 */
class RouteReloadBeanClassTest extends YamlTestSupport {

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
    private Path punctuation;

    @Override
    public void doSetup() throws Exception {
        dir = Files.createTempDirectory("camel-reload");
        punctuation = dir.resolve("Punctuation.java");
        writePunctuation("!");
        Path greeter = dir.resolve("Greeter.java");
        Files.writeString(greeter, """
                package com.example;

                public class Greeter {
                    private Punctuation punctuation;

                    public void setPunctuation(Punctuation punctuation) {
                        this.punctuation = punctuation;
                    }

                    public String greet(String name) {
                        return "Hello " + name + punctuation.mark();
                    }
                }
                """);
        Path beans = dir.resolve("beans.yaml");
        Files.writeString(beans, """
                - beans:
                    - name: punctuation
                      type: com.example.Punctuation
                    - name: greeter
                      type: com.example.Greeter
                      properties:
                        punctuation: "#bean:punctuation"
                """);
        Path route = dir.resolve("route.camel.yaml");
        Files.writeString(route, """
                - route:
                    id: greeting
                    from:
                      uri: direct:greet
                      steps:
                        - bean:
                            ref: greeter
                            method: greet
                """);
        context.start();
        PluginHelper.getRoutesLoader(context).loadRoutes(List.of(
                ResourceHelper.resolveResource(context, "file:" + route),
                ResourceHelper.resolveResource(context, "file:" + beans),
                ResourceHelper.resolveResource(context, "file:" + punctuation),
                ResourceHelper.resolveResource(context, "file:" + greeter)));
    }

    @Override
    public void doCleanup() throws Exception {
        deleteRecursively(dir);
    }

    @Test
    void theBeansAreCreatedAgainWhenAClassChanges() throws Exception {
        TestableStrategy strategy = new TestableStrategy(dir.toString());
        strategy.setCamelContext(context);
        strategy.setPattern("*");
        // the same as camel-main: the files that declare beans
        strategy.setBeanResources(() -> {
            Set<Resource> answer = new LinkedHashSet<>();
            for (BeanFactoryDefinition<?> bean : context.getCamelContextExtension().getContextPlugin(Model.class)
                    .getCustomBeans()) {
                answer.add(bean.getResource());
            }
            return answer;
        });
        // the strategy is not started (no file watcher in the test): its reload callback is driven by hand
        strategy.doStart();
        try {
            assertThat(context.createProducerTemplate().requestBody("direct:greet", "Camel", String.class))
                    .isEqualTo("Hello Camel!");

            // only the class of the bean that greeter calls changes
            writePunctuation("?");
            strategy.getResourceReload().onReload(punctuation.toString(),
                    ResourceHelper.resolveResource(context, "file:" + punctuation));

            assertThat(context.createProducerTemplate().requestBody("direct:greet", "Camel", String.class))
                    .isEqualTo("Hello Camel?");
        } finally {
            strategy.doStop();
        }
    }

    private void writePunctuation(String mark) throws IOException {
        Files.writeString(punctuation, """
                package com.example;

                public class Punctuation {
                    public String mark() {
                        return "%s";
                    }
                }
                """.formatted(mark));
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
