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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for RepackageMojo to verify Spring Boot loader integration.
 */
public class RepackageMojoTest {

    private static final String MAIN_CLASS = "org.apache.camel.dsl.jbang.launcher.CamelLauncher";
    private static final String LAUNCHER_CLASS_ENTRY = "org/apache/camel/dsl/jbang/launcher/CamelLauncher.class";
    private static final String DEPENDENCY_JAR_NAME = "camel-jbang-core-1.0.jar";
    private static final String FINAL_NAME = "camel-launcher-1.0";
    private static final String PINNED_TIMESTAMP = "2026-04-25T11:23:57Z";
    private static final long FIXED_ENTRY_TIME = 1000000000000L;

    @TempDir
    File tempDir;

    private int runCounter;

    @Test
    public void testSpringBootLoaderStructure() throws Exception {
        // The launcher is shipped as a single self-executing JAR, which only works if the repackaged
        // artifact carries the loader classes at the root, the application classes under BOOT-INF/classes
        // and the dependencies under BOOT-INF/lib. Losing any of the three yields a JAR that builds
        // cleanly and then fails at 'java -jar' time.
        File repackaged = repackage(PINNED_TIMESTAMP);
        Set<String> entries = entryNames(repackaged);

        assertTrue(entries.contains("BOOT-INF/classes/" + LAUNCHER_CLASS_ENTRY),
                "application classes must be relocated under BOOT-INF/classes, but found: " + entries);
        assertTrue(entries.contains("BOOT-INF/lib/" + DEPENDENCY_JAR_NAME),
                "dependencies must be nested under BOOT-INF/lib, but found: " + entries);
        assertTrue(entries.stream().anyMatch(e -> e.startsWith("org/springframework/boot/loader/")),
                "the Spring Boot loader classes must sit at the JAR root so 'java -jar' can bootstrap");
    }

    @Test
    public void testManifestEntries() throws Exception {
        // 'java -jar' dispatches through Main-Class, so it must name the loader, and the loader in turn
        // reads Start-Class to find the real entry point. Swapping the two silently produces a JAR that
        // cannot start.
        File repackaged = repackage(PINNED_TIMESTAMP);

        try (JarFile jar = new JarFile(repackaged)) {
            Attributes attributes = jar.getManifest().getMainAttributes();

            assertEquals("org.springframework.boot.loader.launch.JarLauncher", attributes.getValue("Main-Class"),
                    "Main-Class must be the Spring Boot launcher, not the application entry point");
            assertEquals(MAIN_CLASS, attributes.getValue("Start-Class"),
                    "Start-Class must be the configured application entry point");
        }
    }

    @Test
    public void testDependencyInclusion() throws Exception {
        // The filtering in includeArtifact only matters if it actually reaches the packaged output: a
        // test-scoped jar must stay out of the distribution, and camel-exe:exe must never be embedded
        // as a loader library (it is a native Windows binary staged by the assembly instead).
        Artifact compileDependency = artifactWithFile("org.apache.camel", "camel-jbang-core", "jar",
                Artifact.SCOPE_COMPILE);
        Artifact testDependency = artifactWithFile("org.junit.jupiter", "junit-jupiter", "jar", Artifact.SCOPE_TEST);
        Artifact nativeExe = artifactWithFile("org.apache.camel", "camel-exe", "exe", Artifact.SCOPE_COMPILE);

        File repackaged = repackage(PINNED_TIMESTAMP, compileDependency, testDependency, nativeExe);
        Set<String> libraries = entryNames(repackaged).stream()
                .filter(e -> e.startsWith("BOOT-INF/lib/") && e.endsWith(".jar"))
                .collect(Collectors.toSet());

        assertTrue(libraries.contains("BOOT-INF/lib/" + DEPENDENCY_JAR_NAME),
                "the compile-scoped jar must be nested as a loader library, but found: " + libraries);
        assertFalse(libraries.stream().anyMatch(e -> e.contains("junit-jupiter")),
                "a test-scoped dependency must not ship in the distribution, but found: " + libraries);
        assertFalse(libraries.stream().anyMatch(e -> e.contains("camel-exe")),
                "the native camel-exe bootstrap must not be embedded as a loader library, but found: " + libraries);
    }

    @Test
    public void testRepackagingIsReproducible() throws Exception {
        // Two builds of the same source must produce byte-identical JARs. The launcher distribution's
        // SHA-256 is published in the WinGet manifest and voted on during the release, so a JAR that
        // differs run to run leaves a digest nobody can independently regenerate.
        File first = repackage(PINNED_TIMESTAMP);
        File second = repackage(PINNED_TIMESTAMP);

        assertArrayEquals(Files.readAllBytes(first.toPath()), Files.readAllBytes(second.toPath()),
                "repackaging the same input twice must produce identical bytes");
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
        // bin/camel-x64.exe and bin/camel-arm64.exe; it must never end up embedded as a Spring
        // Boot loader library.
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

    @Test
    public void testIsoOutputTimestampIsParsed() {
        // Passing a non-null time to the repackager is what makes the JAR reproducible: it pins every
        // entry's modification time and switches BOOT-INF/lib to a sorted map. Without it, two builds
        // of the same source produce different bytes, and the launcher archive's published SHA-256
        // cannot be regenerated by anyone verifying a release.
        FileTime time = RepackageMojo.parseOutputTimestamp("2026-04-25T11:23:57Z");

        assertNotNull(time, "an ISO-8601 project.build.outputTimestamp must yield a pinned time");
        assertEquals(Instant.parse("2026-04-25T11:23:57Z"), time.toInstant(),
                "the pinned time must be the configured instant, not the build's wall-clock time");
    }

    @Test
    public void testEpochSecondsOutputTimestampIsParsed() {
        // Maven also accepts the SOURCE_DATE_EPOCH form: an integer of seconds since the epoch.
        FileTime time = RepackageMojo.parseOutputTimestamp("1777029837");

        assertNotNull(time, "a numeric project.build.outputTimestamp must yield a pinned time");
        assertEquals(Instant.ofEpochSecond(1777029837L), time.toInstant(),
                "the pinned time must be the configured epoch second");
    }

    @Test
    public void testUnsetOutputTimestampLeavesTimeUnpinned() {
        // A single character is Maven's documented way to disable an inherited timestamp. Returning
        // null keeps the previous behaviour rather than pinning entries to some arbitrary default.
        assertNull(RepackageMojo.parseOutputTimestamp(null),
                "an absent project.build.outputTimestamp must leave the build unpinned");
        assertNull(RepackageMojo.parseOutputTimestamp("-"),
                "a single-character project.build.outputTimestamp disables reproducible output");
    }

    @Test
    public void testInvalidOutputTimestampFailsLoudly() {
        // A malformed value must not silently degrade to a non-reproducible build: the release would
        // then publish a digest nobody can reproduce, and nothing would report why.
        assertThrows(IllegalArgumentException.class, () -> RepackageMojo.parseOutputTimestamp("not-a-date"),
                "an unparseable project.build.outputTimestamp must fail the build");
    }

    private static Artifact artifact(String groupId, String artifactId, String type, String scope) {
        return new DefaultArtifact(groupId, artifactId, "1.0", scope, type, null, new DefaultArtifactHandler(type));
    }

    /**
     * Runs the mojo over a freshly staged source JAR, returning the repackaged result. Each call gets its own output
     * directory so repeated invocations stay independent.
     */
    private File repackage(String outputTimestamp, Artifact... dependencies) throws Exception {
        File outputDirectory = new File(tempDir, "build-" + (runCounter++));
        assertTrue(outputDirectory.mkdirs(), "failed to create " + outputDirectory);

        File sourceJar = new File(outputDirectory, FINAL_NAME + ".jar");
        writeJar(sourceJar, LAUNCHER_CLASS_ENTRY);

        MavenProject project = new MavenProject();
        project.setArtifacts(Set.of(dependencies));

        RepackageMojo mojo = new RepackageMojo();
        mojo.project = project;
        mojo.outputDirectory = outputDirectory;
        mojo.finalName = FINAL_NAME;
        mojo.mainClass = MAIN_CLASS;
        mojo.backupSource = false;
        mojo.outputTimestamp = outputTimestamp;
        mojo.execute();

        return new File(outputDirectory, FINAL_NAME + ".jar");
    }

    private File repackage(String outputTimestamp) throws Exception {
        return repackage(outputTimestamp, artifactWithFile("org.apache.camel", "camel-jbang-core", "jar",
                Artifact.SCOPE_COMPILE));
    }

    /**
     * Builds an artifact backed by a real JAR on disk. Spring Boot's packager only nests entries it can open as a zip,
     * so the file has to be a genuine archive rather than a placeholder.
     */
    private Artifact artifactWithFile(String groupId, String artifactId, String type, String scope) throws IOException {
        Artifact artifact = artifact(groupId, artifactId, type, scope);
        File file = new File(tempDir, artifactId + "-1.0." + type);
        if (!file.exists()) {
            writeJar(file, "org/apache/camel/Placeholder.class");
        }
        artifact.setFile(file);
        return artifact;
    }

    /**
     * Writes a minimal but valid JAR. Entry times are fixed so the fixture itself cannot introduce the very
     * non-determinism {@link #testRepackagingIsReproducible()} is checking for.
     */
    private static void writeJar(File file, String entryName) throws IOException {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(file.toPath()), manifest)) {
            JarEntry entry = new JarEntry(entryName);
            entry.setTime(FIXED_ENTRY_TIME);
            out.putNextEntry(entry);
            out.write(entryName.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
    }

    private static Set<String> entryNames(File jar) throws IOException {
        try (JarFile jarFile = new JarFile(jar)) {
            return jarFile.stream().map(JarEntry::getName).collect(Collectors.toSet());
        }
    }
}
