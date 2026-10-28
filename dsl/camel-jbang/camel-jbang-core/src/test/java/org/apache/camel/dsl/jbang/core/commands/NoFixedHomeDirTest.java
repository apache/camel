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
package org.apache.camel.dsl.jbang.core.commands;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The tests run in parallel forks (AGENTS.md of this module), so a home of the CLI given as a fixed directory, such as
 * one under target, is used by the tests of the other forks at the same time. A test that sets the home with a string
 * or a path of literals fails here: it calls useTestHomeDir() of CamelCommandBaseTestSupport instead, or sets a
 * temporary directory as the home and restores the previous home after the test.
 */
class NoFixedHomeDirTest {

    private static final Path SOURCES = Path.of("src/test/java");
    private static final Pattern FIXED_HOME = Pattern.compile("useHomeDir\\(\\s*(\"|(Paths\\.get|Path\\.of)\\(\\s*\")");

    @Test
    void noTestUsesAFixedHome() throws IOException {
        List<String> problems = new ArrayList<>();
        for (Path file : sources()) {
            List<String> lines = Files.readAllLines(file);
            for (int i = 0; i < lines.size(); i++) {
                if (FIXED_HOME.matcher(lines.get(i)).find()) {
                    problems.add(SOURCES.relativize(file) + ":" + (i + 1) + ": " + lines.get(i).strip());
                }
            }
        }
        assertThat(problems)
                .as("A fixed home is shared with the tests of the other forks: call useTestHomeDir() of"
                    + " CamelCommandBaseTestSupport, or set a @TempDir as the home and restore the previous home in"
                    + " @AfterEach")
                .isEmpty();
    }

    private static List<Path> sources() throws IOException {
        try (Stream<Path> files = Files.walk(SOURCES)) {
            return files.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.getFileName().toString().equals(NoFixedHomeDirTest.class.getSimpleName() + ".java"))
                    .sorted().toList();
        }
    }
}
