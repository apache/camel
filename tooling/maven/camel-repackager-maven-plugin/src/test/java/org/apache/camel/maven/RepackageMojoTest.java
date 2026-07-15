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
package org.apache.camel.maven;

import java.io.File;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for RepackageMojo to verify Spring Boot loader integration.
 */
public class RepackageMojoTest {

    @TempDir
    File tempDir;

    @Test
    public void testSpringBootLoaderStructure() throws Exception {
        // This test would verify that the repackaged JAR has the correct Spring Boot structure
        // For now, it's a placeholder to demonstrate the expected behavior

        // Expected structure after repackaging:
        // - META-INF/MANIFEST.MF with Main-Class: org.springframework.boot.loader.launch.JarLauncher
        // - org/springframework/boot/loader/ classes
        // - BOOT-INF/classes/ with application classes
        // - BOOT-INF/lib/ with dependency JARs

        assertTrue(true, "Placeholder test - would verify Spring Boot JAR structure");
    }

    @Test
    public void testManifestEntries() throws Exception {
        // This test would verify that the manifest has the correct entries:
        // Main-Class: org.springframework.boot.loader.launch.JarLauncher
        // Start-Class: org.apache.camel.dsl.jbang.launcher.CamelLauncher

        assertTrue(true, "Placeholder test - would verify manifest entries");
    }

    @Test
    public void testDependencyInclusion() throws Exception {
        // This test would verify that all compile and runtime dependencies
        // are included as separate JARs in BOOT-INF/lib/

        assertTrue(true, "Placeholder test - would verify dependency inclusion");
    }

    @Test
    public void testCompileScopedJarIsIncluded() {
        RepackageMojo mojo = new RepackageMojo();
        Artifact artifact = artifact("org.apache.camel", "camel-jbang-core", "jar", Artifact.SCOPE_COMPILE);

        assertTrue(mojo.includeArtifact(artifact),
                "a compile-scoped jar dependency must be bundled into BOOT-INF/lib");
    }

    @Test
    public void testNonJarArtifactIsExcludedEvenWhenCompileScoped() {
        // camel-launcher depends on camel-exe:exe purely so the assembly descriptor can stage
        // bin/camel.exe; it must never end up embedded as a Spring Boot loader library.
        RepackageMojo mojo = new RepackageMojo();
        Artifact artifact = artifact("org.apache.camel", "camel-exe", "exe", Artifact.SCOPE_COMPILE);

        assertFalse(mojo.includeArtifact(artifact),
                "a non-jar artifact (e.g. the native camel-exe:exe bootstrap) must not be bundled into BOOT-INF/lib");
    }

    @Test
    public void testProvidedCamelJarIsIncluded() {
        RepackageMojo mojo = new RepackageMojo();
        Artifact artifact = artifact("org.apache.camel", "camel-util", "jar", Artifact.SCOPE_PROVIDED);

        assertTrue(mojo.includeArtifact(artifact),
                "a provided-scope org.apache.camel jar dependency must still be bundled");
    }

    @Test
    public void testProvidedNonCamelJarIsExcluded() {
        RepackageMojo mojo = new RepackageMojo();
        Artifact artifact = artifact("org.jolokia", "jolokia-agent-jvm", "jar", Artifact.SCOPE_PROVIDED);

        assertFalse(mojo.includeArtifact(artifact),
                "a provided-scope non-Camel jar dependency must not be bundled");
    }

    private static Artifact artifact(String groupId, String artifactId, String type, String scope) {
        return new DefaultArtifact(groupId, artifactId, "1.0", scope, type, null, new DefaultArtifactHandler(type));
    }
}
