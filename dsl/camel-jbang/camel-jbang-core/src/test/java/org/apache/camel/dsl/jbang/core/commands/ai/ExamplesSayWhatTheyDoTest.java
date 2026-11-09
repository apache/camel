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
package org.apache.camel.dsl.jbang.core.commands.ai;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every bundled example says what it does in its own sources, as the About view of the TUI and camel get about show it
 * first: the comment at the top of a route file, else the descriptions of its routes.
 */
class ExamplesSayWhatTheyDoTest {

    private static final Path EXAMPLES = Path.of("src/main/resources/examples");

    @Test
    void everyExampleHasAPurpose() throws IOException {
        CamelCatalog catalog = new DefaultCamelCatalog();
        List<String> without = new ArrayList<>();
        int examples = 0;
        try (Stream<Path> groups = Files.list(EXAMPLES)) {
            for (Path group : groups.filter(Files::isDirectory).sorted().toList()) {
                try (Stream<Path> dirs = Files.list(group)) {
                    for (Path dir : dirs.filter(d -> Files.isRegularFile(d.resolve("README.md"))).sorted().toList()) {
                        examples++;
                        if (About.purpose(dir, ProjectOverview.analyze(dir, catalog)) == null) {
                            without.add(EXAMPLES.relativize(dir).toString());
                        }
                    }
                }
            }
        }
        assertThat(examples).isGreaterThan(25);
        assertThat(without).as("examples without a comment at the top of a route file or a README").isEmpty();
    }
}
