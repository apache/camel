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

package org.apache.camel.dsl.jbang.core.commands.test;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.camel.dsl.jbang.core.commands.ExportHelper;
import org.apache.camel.dsl.jbang.core.common.PluginExporter;
import org.apache.camel.dsl.jbang.core.common.Printer;
import org.apache.camel.dsl.jbang.core.common.RuntimeType;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StringHelper;
import org.citrusframework.CitrusSettings;
import org.citrusframework.CitrusVersion;
import org.citrusframework.util.FileUtils;

/**
 * Exporter adds test related source files, build properties and dependencies to the exported project. Exporter looks
 * for test sources in a specific subfolder relative to the Camel integration. When tests are present the exporter will
 * add test scoped dependencies and add the tests to the build directory In Maven this will be the src/test/java and
 * src/test/resources directory.
 */
public class TestPluginExporter implements PluginExporter {

    public static final Pattern NAME_SEPARATOR_PATTERN = Pattern.compile("[-._]([a-z])");

    @Override
    public boolean isEnabled() {
        // assume tests are located in a subdirectory, only perform when this directory is present
        return Files.exists(Path.of(".").resolve("test"));
    }

    @Override
    public Properties getBuildProperties() {
        Properties props = new Properties();

        if (isEnabled()) {
            props.setProperty("citrus.version", CitrusVersion.version());
        }

        return props;
    }

    @Override
    public Set<String> getDependencies(RuntimeType runtime) {
        Set<String> deps = new HashSet<>();

        if (!isEnabled()) {
            return deps;
        }

        // add default Citrus dependencies as defined in Citrus JBang
        deps.add(asDependency("citrus-base"));
        deps.add(asDependency("citrus-junit5"));
        deps.add(asDependency("citrus-http"));
        deps.add(asDependency("citrus-yaml"));
        deps.add(asDependency("citrus-xml"));
        deps.add(asDependency("citrus-groovy"));
        deps.add(asDependency("citrus-validation-xml"));
        deps.add(asDependency("citrus-validation-json"));
        deps.add(asDependency("citrus-validation-yaml"));

        Path testDir = Path.of(".").resolve("test");
        if (Files.exists(testDir.resolve("jbang.properties"))) {
            try (FileInputStream fis =
                    new FileInputStream(testDir.resolve("jbang.properties").toFile())) {
                Properties props = new Properties();
                props.load(fis);

                // read runtime dependencies from jbang-.properties
                String[] dependencies =
                        props.getOrDefault("run.deps", "").toString().split(",");
                for (String dependency : dependencies) {
                    if (dependency.startsWith("org.citrusframework:")) {
                        // construct proper Citrus Maven GAV from just the artifact id
                        deps.add(asDependency(extractArtifactId(dependency)));
                    } else if (dependency.startsWith("org.apache.camel")) {
                        // remove version from GAV, because we generally use the Camel bom
                        String[] parts = dependency.split(":");
                        deps.add("mvn@test:%s:%s".formatted(parts[0], parts[1]));
                    } else {
                        // add as test scoped dependency
                        deps.add("mvn@test:" + dependency);
                    }
                }
            } catch (IOException e) {
                // ignore IO error while reading jbang.properties
            }
        }

        return deps;
    }

    @Override
    public void addSourceFiles(Path buildDir, String packageName, Printer printer) throws Exception {
        if (!isEnabled()) {
            return;
        }

        Path srcTestSrcDir = buildDir.resolve("src/test/java");
        Path srcTestResourcesDir = buildDir.resolve("src/test/resources");

        Files.createDirectories(srcTestSrcDir);
        Files.createDirectories(srcTestResourcesDir);

        Path testDir = Path.of(".").resolve("test");
        Path testProfile = testDir.resolve("application.test.properties");
        if (Files.exists(testProfile)) {
            ExportHelper.safeCopy(testProfile, srcTestResourcesDir.resolve("application.test.properties"), true);
        }

        try (Stream<Path> paths = Files.list(testDir)) {
            // Add all supported test sources
            Set<Path> testSources = paths.filter(
                            path -> !path.getFileName().toString().startsWith("."))
                    .filter(path -> {
                        String ext =
                                FileUtils.getFileExtension(path.getFileName().toString());
                        return CitrusSettings.getTestFileNamePattern(ext).stream()
                                .map(Pattern::compile)
                                .anyMatch(pattern -> pattern.matcher(
                                                path.getFileName().toString())
                                        .matches());
                    })
                    .collect(Collectors.toSet());

            for (Path testSource : testSources) {
                String ext = FileUtils.getFileExtension(testSource.getFileName().toString());
                if (ext.equals("java")) {
                    Path javaSource;
                    if (packageName != null) {
                        javaSource = srcTestSrcDir.resolve(
                                packageName.replaceAll("\\.", "/") + "/" + testSource.getFileName());
                    } else {
                        javaSource = srcTestSrcDir.resolve(testSource.getFileName());
                    }

                    ExportHelper.safeCopy(
                            new ByteArrayInputStream(readTestSource(testSource).getBytes(StandardCharsets.UTF_8)),
                            javaSource);
                } else {
                    Path resource = srcTestResourcesDir.resolve(testSource.getFileName());
                    ExportHelper.safeCopy(
                            new ByteArrayInputStream(readTestSource(testSource).getBytes(StandardCharsets.UTF_8)),
                            resource);

                    String javaClassName = getJavaClassName(
                            FileUtils.getBaseName(testSource.getFileName().toString()));
                    Path javaSource;
                    if (packageName != null) {
                        javaSource = srcTestSrcDir.resolve(
                                packageName.replaceAll("\\.", "/") + "/" + javaClassName + ".java");
                    } else {
                        javaSource = srcTestSrcDir.resolve(javaClassName + ".java");
                    }

                    try (InputStream is =
                            TestPlugin.class.getClassLoader().getResourceAsStream("templates/junit-test.tmpl")) {
                        String context = IOHelper.loadText(is);

                        context = context.replaceAll(
                                "\\{\\{ \\.PackageDeclaration }}", getPackageDeclaration(packageName));
                        context = context.replaceAll("\\{\\{ \\.Type }}", ext);
                        context = context.replaceAll("\\{\\{ \\.Name }}", javaClassName);
                        context =
                                context.replaceAll("\\{\\{ \\.MethodName }}", StringHelper.decapitalize(javaClassName));
                        context = context.replaceAll(
                                "\\{\\{ \\.ResourcePath }}",
                                testSource.getFileName().toString());

                        ExportHelper.safeCopy(
                                new ByteArrayInputStream(context.getBytes(StandardCharsets.UTF_8)), javaSource);
                    }
                }
            }
        }
    }

    /**
     * Read and process given test source. Apply common postprocessing steps on test source code. For instance makes
     * sure that relative file paths get replaced with proper classpath resource paths.
     */
    private String readTestSource(Path source) throws IOException {
        String context = Files.readString(source, StandardCharsets.UTF_8);
        context = context.replaceAll("\\.\\./", "camel/");
        return context;
    }

    private String getPackageDeclaration(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return "";
        } else {
            return "package %s;%n%n".formatted(packageName);
        }
    }

    /**
     * Get proper Java class name from file resource name. Make sure to replace Java disallowed characters with camel
     * case style.
     */
    private String getJavaClassName(String resourceName) {
        Matcher matcher = NAME_SEPARATOR_PATTERN.matcher(resourceName);

        StringBuilder sb = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            sb.append(resourceName, last, matcher.start());
            sb.append(matcher.group(0).toUpperCase());
            last = matcher.end();
        }
        sb.append(resourceName.substring(last));

        String javaClassName = StringHelper.capitalize(sb.toString().replaceAll("\\W", ""));

        if (javaClassName.endsWith("It")) {
            javaClassName = javaClassName.substring(0, javaClassName.length() - 1) + "T";
        }

        return javaClassName;
    }

    private String extractArtifactId(String dependency) {
        return dependency.split(":")[1];
    }

    private String asDependency(String artifactName) {
        return "mvn@test:org.citrusframework:%s:\\$\\{citrus.version}".formatted(artifactName);
    }
}
