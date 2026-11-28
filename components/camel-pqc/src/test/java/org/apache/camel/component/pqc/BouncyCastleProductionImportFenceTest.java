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
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Enforces zero reflection across PQC production sources and restricts BC imports to typed support classes. */
class BouncyCastleProductionImportFenceTest {

    private static final Pattern BC_IMPORT = Pattern.compile(
            "(?m)^\\s*import\\s+(?:static\\s+)?org\\.bouncycastle\\.[A-Za-z0-9_.$*]+\\s*;");
    private static final Pattern REFLECTION = Pattern.compile(
            "java\\.lang\\.reflect|java\\.lang\\.invoke|" +
                                                              "Class\\.forName\\s*\\(|getDeclared(?:Constructor|Method|Field)s?\\s*\\(|"
                                                              +
                                                              "get(?:Constructor|Method|Field)s?\\s*\\(|(?:setAccessible|trySetAccessible)\\s*\\(|\\.invoke\\s*\\(|"
                                                              +
                                                              "(?:Constructor|Method|Field)\\s*\\.newInstance\\s*\\(|Class\\.newInstance\\s*\\(");
    private static final List<String> ALLOWED_BC_FILES = List.of(
            "BouncyCastleSupport.java", "BouncyCastleKemSupport.java",
            "BouncyCastlePqcCompatibility.java");

    @Test
    void productionBcImportsStayInsideTypedSupportClasses() throws IOException {
        Path sourceRoot = Path.of("src/main/java");
        assertTrue(Files.isDirectory(sourceRoot), "Test must run from the camel-pqc module directory");
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                if (!ALLOWED_BC_FILES.contains(file.getFileName().toString()) && BC_IMPORT.matcher(source).find()) {
                    violations.add(file + ": BC imports are outside the approved typed boundary");
                }
            }
        }
        assertTrue(violations.isEmpty(), () -> String.join("\n", violations));
    }

    @Test
    void allPqcProductionSourcesContainNoReflection() throws IOException {
        Path sourceRoot = Path.of("src/main/java");
        assertTrue(Files.isDirectory(sourceRoot), "Missing camel-pqc production source root");
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                if (REFLECTION.matcher(Files.readString(file)).find()) {
                    violations.add(file + ": reflective access detected");
                }
            }
        }
        assertTrue(violations.isEmpty(), () -> String.join("\n", violations));
    }

}
