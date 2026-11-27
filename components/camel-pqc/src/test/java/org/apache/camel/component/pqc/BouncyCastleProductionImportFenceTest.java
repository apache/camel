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
package org.apache.camel.component.pqc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ensures that production code keeps Bouncy Castle imports outside the Camel PQC component. */
class BouncyCastleProductionImportFenceTest {

    private static final Pattern BC_IMPORT = Pattern.compile(
            "(?m)^\\s*import\\s+(?:static\\s+)?org\\.bouncycastle\\.[A-Za-z0-9_.$*]+\\s*;");

    @Test
    void productionSourcesDoNotImportBouncyCastleTypes() throws IOException {
        Path sourceRoot = Path.of("src/main/java");
        assertTrue(Files.isDirectory(sourceRoot), "Test must run from the camel-pqc module directory");

        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                Matcher matcher = BC_IMPORT.matcher(Files.readString(file));
                while (matcher.find()) {
                    violations.add(file + ": " + matcher.group().trim());
                }
            }
        }

        assertTrue(violations.isEmpty(),
                () -> "Bouncy Castle imports are restricted in camel-pqc production sources:\n"
                      + String.join("\n", violations));
    }
}
