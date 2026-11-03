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
package org.apache.camel.dsl.jbang.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.Attributes;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The layout of the launcher distributions: the launcher JAR in lib/, whose manifest names the other JARs in lib/ as
 * its class path, and no JAR in bin/ but the Jolokia agent. Runs during {@code verify} when
 * {@code -Dcamel.exe.build=true} is set, as {@link CamelLauncherNativeExeIT} does.
 */
@EnabledIfSystemProperty(named = "camel.exe.build", matches = "true")
class LauncherArchiveLayoutIT {

    private static final Path TARGET = Paths.get("target");

    @Test
    void theLauncherJarHasTheJarsOfLibAsItsClassPath() throws IOException {
        List<Path> zips = binZips();
        assertThat(zips).as("the public and the WinGet ZIP").hasSize(2);
        for (Path zip : zips) {
            try (ZipFile archive = new ZipFile(zip.toFile())) {
                Set<String> lib = new TreeSet<>();
                Set<String> binJars = new TreeSet<>();
                ZipEntry launcher = null;
                for (ZipEntry entry : archive.stream().toList()) {
                    String[] path = entry.getName().split("/");
                    if (entry.isDirectory() || path.length != 3) {
                        continue;
                    }
                    if ("lib".equals(path[1])) {
                        if (path[2].matches("camel-launcher-[^/]*\\.jar")) {
                            launcher = entry;
                        } else {
                            lib.add(path[2]);
                        }
                    } else if ("bin".equals(path[1]) && path[2].endsWith(".jar")) {
                        binJars.add(path[2]);
                    }
                }
                assertThat(launcher).as("lib/camel-launcher-<version>.jar in " + zip.getFileName()).isNotNull();
                assertThat(binJars).as("the JARs in bin/ of " + zip.getFileName())
                        .allMatch(jar -> jar.startsWith("jolokia-agent-jvm-") && jar.endsWith("-javaagent.jar"));

                Manifest manifest;
                try (InputStream in = archive.getInputStream(launcher); JarInputStream jar = new JarInputStream(in)) {
                    manifest = jar.getManifest();
                }
                Attributes attributes = manifest.getMainAttributes();
                assertThat(attributes.getValue(Attributes.Name.MAIN_CLASS)).isEqualTo(CamelLauncher.class.getName());
                Set<String> classPath = new TreeSet<>(List.of(attributes.getValue(Attributes.Name.CLASS_PATH).split(" ")));
                assertThat(classPath).as("the class path of the launcher JAR in " + zip.getFileName())
                        .isEqualTo(lib);
            }
        }
    }

    private static List<Path> binZips() throws IOException {
        try (Stream<Path> paths = Files.list(TARGET)) {
            return new ArrayList<>(
                    paths
                            .filter(p -> p.getFileName().toString().matches("camel-launcher-.*-bin\\.zip"))
                            .toList());
        }
    }
}
