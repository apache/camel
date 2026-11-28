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
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Enforces zero reflection across PQC production sources and restricts BC imports to typed public boundaries. */
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
            "BouncyCastleSupport.java", "BouncyCastleKemSupport.java");

    @Test
    void productionBcImportsStayInsideTheTwoTypedJcaBoundaries() throws IOException {
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
    void allPqcProductionAndAdapterSourcesContainNoReflection() throws IOException {
        List<Path> roots = List.of(
                Path.of("src/main/java"),
                Path.of("../camel-pqc-bc-compat-api/src/main/java"),
                Path.of("../camel-pqc-bc-compat-186/src/main/java"),
                Path.of("../camel-pqc-bc-compat-187/src/main/java"),
                Path.of("../camel-pqc-micrometer/src/main/java"));
        List<String> violations = new ArrayList<>();
        for (Path root : roots) {
            assertTrue(Files.isDirectory(root), "Missing production source root: " + root);
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                    String source = Files.readString(file);
                    if (REFLECTION.matcher(source).find()) {
                        violations.add(file + ": reflective access detected");
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(), () -> String.join("\n", violations));
    }

    @Test
    void bc187AdapterDoesNotImportDeprecatedLmsXmssCompatibilityAliases() throws IOException {
        Path sourceRoot = Path.of("../camel-pqc-bc-compat-187/src/main/java");
        assertTrue(Files.isDirectory(sourceRoot), "Missing BC 1.87 adapter source root");
        Set<String> deprecatedAliases = Set.of(
                "org.bouncycastle.pqc.jcajce.spec.LMSParameterSpec",
                "org.bouncycastle.pqc.jcajce.spec.LMSHSSParameterSpec",
                "org.bouncycastle.pqc.jcajce.spec.LMSKeyGenParameterSpec",
                "org.bouncycastle.pqc.jcajce.spec.LMSHSSKeyGenParameterSpec",
                "org.bouncycastle.pqc.jcajce.spec.XMSSParameterSpec",
                "org.bouncycastle.pqc.jcajce.spec.XMSSMTParameterSpec",
                "org.bouncycastle.pqc.jcajce.interfaces.LMSKey",
                "org.bouncycastle.pqc.jcajce.interfaces.LMSPrivateKey",
                "org.bouncycastle.pqc.jcajce.interfaces.XMSSKey",
                "org.bouncycastle.pqc.jcajce.interfaces.XMSSPrivateKey",
                "org.bouncycastle.pqc.jcajce.interfaces.XMSSMTKey",
                "org.bouncycastle.pqc.jcajce.interfaces.XMSSMTPrivateKey");
        Pattern imports = Pattern.compile(
                "(?m)^\\s*import\\s+(?:static\\s+)?(org\\.bouncycastle\\.[A-Za-z0-9_.$*]+)\\s*;");
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                Matcher matcher = imports.matcher(Files.readString(file));
                while (matcher.find()) {
                    if (deprecatedAliases.contains(matcher.group(1))) {
                        violations.add(file + ": deprecated BC 1.87 alias " + matcher.group(1));
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(), () -> String.join("\n", violations));
    }
}
