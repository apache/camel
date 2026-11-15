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
package org.apache.camel.dsl.jbang.run.export;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.xpath.XPathFactory;

import org.apache.camel.dsl.jbang.run.RunPipeline;
import org.apache.camel.dsl.jbang.run.common.CamelHome;
import org.apache.camel.dsl.jbang.run.common.ExportHelper;
import org.apache.camel.dsl.jbang.run.common.MavenResolver;
import org.apache.camel.dsl.jbang.run.common.PathUtils;
import org.apache.camel.dsl.jbang.run.common.QuarkusPlatform;
import org.apache.camel.dsl.jbang.run.common.RuntimeType;
import org.apache.camel.dsl.jbang.run.common.StringPrinter;
import org.apache.camel.dsl.jbang.run.common.XmlHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junitpioneer.jupiter.SetSystemProperty;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exports through the Java API, without the CLI: the dependencies come from a resolver that writes the run settings, as
 * the silent run of camel-jbang-core does.
 */
class ProjectExporterTest {

    // the home of the CLI, where an export locks its work directory
    @TempDir
    Path home;
    private Path previousHome;

    @TempDir
    Path dir;

    private final List<ExportRequest> resolved = Collections.synchronizedList(new ArrayList<>());

    private final DependencyResolver resolver = request -> {
        resolved.add(request);
        Path settings = request.workDir.resolve(ExportHelper.RUN_SETTINGS_FILE);
        Files.createDirectories(settings.getParent());
        Files.writeString(settings, """
                camel.main.routesIncludePattern=file:%s
                dependency=camel:timer
                dependency=camel:log
                """.formatted(request.files.get(0)));
        return 0;
    };

    @BeforeEach
    void useHome() {
        previousHome = CamelHome.getHomeDir();
        CamelHome.useHomeDir(home.toString());
    }

    @AfterEach
    void restoreHome() {
        CamelHome.useHomeDir(previousHome.toString());
    }

    @Test
    void exportsCamelMainProject() throws Exception {
        Path route = writeRoute("hello");
        Path exportDir = dir.resolve("export");
        ExportRequest request = request(route, exportDir);

        StringPrinter printer = new StringPrinter();
        ExportContext context = new ExportContext().withPrinter(printer).withDependencyResolver(resolver);
        int exit = new ProjectExporter(context).export(request);

        assertThat(exit).as(printer.getOutput()).isZero();
        // the GAV is derived from the name of the first file
        assertThat(request.gav).isEqualTo("org.example.project:hello:1.0-SNAPSHOT");
        // the resolver gets a copy of the request, not the request of the caller
        assertThat(resolved).singleElement().isNotSameAs(request);

        String pom = Files.readString(exportDir.resolve("pom.xml"));
        assertThat(pom).contains("<artifactId>hello</artifactId>", "<artifactId>camel-timer</artifactId>",
                "<artifactId>camel-log</artifactId>");
        assertThat(exportDir.resolve("src/main/resources/camel/hello.camel.yaml")).hasSameTextualContentAs(route);
        // the run settings and the project are built in the work directory of the request
        assertThat(request.workDir.resolve(ExportHelper.RUN_SETTINGS_FILE)).exists();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = "4.10.0")
    void springBootExportUsesResolvedCoreVersionUnlessExplicit(String camelVersion) throws Exception {
        Path exportDir = dir.resolve("export");
        ExportRequest request = request(writeRoute("hello"), exportDir);
        request.runtime = RuntimeType.springBoot;
        request.camelSpringBootVersion = "4.8.0";
        request.camelVersion = camelVersion;
        DependencyResolver withCoreDependency = r -> {
            resolver.resolve(r);
            Path settings = r.workDir.resolve(ExportHelper.RUN_SETTINGS_FILE);
            Files.writeString(settings, Files.readString(settings) + "dependency=camel:util\n");
            return 0;
        };
        StringPrinter printer = new StringPrinter();
        int exit = new ProjectExporter(
                new ExportContext().withPrinter(printer)
                        .withDependencyResolver(withCoreDependency))
                .export(request);

        assertThat(exit).as(printer.getOutput()).isZero();
        var factory = XmlHelper.createDocumentBuilderFactory();
        factory.setNamespaceAware(false);
        var pom = factory.newDocumentBuilder().parse(exportDir.resolve("pom.xml").toFile());
        var xpath = XPathFactory.newInstance().newXPath();
        assertThat(xpath.evaluate("/project/dependencies/dependency[artifactId='camel-util']/version", pom))
                .isEqualTo(camelVersion == null ? "4.8.0" : camelVersion);
        assertThat(xpath.evaluate(
                "/project/dependencyManagement/dependencies/dependency[artifactId='camel-spring-boot-bom']/version", pom))
                .isEqualTo("4.8.0");
    }

    @Test
    void keepsTheEscapesAndTheEncodingOfTheApplicationProperties() throws Exception {
        Path route = writeRoute("shop");
        // as an editor saves it, in UTF-8
        String source = """
                path=C:\\\\temp\\\\new
                regex=^\\\\d+$
                my.key\\ with\\ space=1
                greeting=Hej æøå
                euro=5 \\u20ac
                """;
        Files.writeString(dir.resolve("application.properties"), source, StandardCharsets.UTF_8);
        Path exportDir = dir.resolve("export");
        ExportRequest request = request(route, exportDir);
        request.baseDir = dir;

        StringPrinter printer = new StringPrinter();
        int exit = new ProjectExporter(new ExportContext().withPrinter(printer).withDependencyResolver(resolver))
                .export(request);
        assertThat(exit).as(printer.getOutput()).isZero();

        // the exported project reads the same properties as camel run reads from the application.properties
        Path exported = exportDir.resolve("src/main/resources/application.properties");
        Properties expected = load(dir.resolve("application.properties"));
        Properties actual = load(exported);
        assertThat(expected.getProperty("path")).isEqualTo("C:\\temp\\new");
        for (String key : List.of("path", "regex", "my.key with space", "greeting", "euro")) {
            assertThat(actual.getProperty(key)).as(key).isEqualTo(expected.getProperty(key));
        }
        // and the UTF-8 text is written as it was, for a runtime that reads it in UTF-8 (Quarkus)
        assertThat(Files.readAllLines(exported, StandardCharsets.UTF_8)).contains("greeting=Hej æøå");
    }

    private static Properties load(Path file) throws Exception {
        Properties answer = new Properties();
        try (InputStream is = Files.newInputStream(file)) {
            answer.load(is);
        }
        return answer;
    }

    @Test
    void keepsTheKeysOfTheApplicationPropertiesThatTheRunSettingsRepeat() throws Exception {
        Path route = writeRoute("shop");
        Files.writeString(dir.resolve("application.properties"), """
                order.period=1000
                camel.server.port=8080
                """);
        // the run settings repeat camel.server.port of the application.properties, as camel run does
        DependencyResolver repeating = r -> {
            Path settings = r.workDir.resolve(ExportHelper.RUN_SETTINGS_FILE);
            Files.createDirectories(settings.getParent());
            Files.writeString(settings, """
                    camel.main.routesIncludePattern=file:%s
                    camel.component.properties.location=file:application.properties
                    camel.server.port=8080
                    dependency=camel:timer
                    dependency=camel:log
                    """.formatted(r.files.get(0)));
            return 0;
        };
        Path exportDir = dir.resolve("export");
        ExportRequest request = request(route, exportDir);
        request.baseDir = dir;

        StringPrinter printer = new StringPrinter();
        int exit = new ProjectExporter(new ExportContext().withPrinter(printer).withDependencyResolver(repeating))
                .export(request);

        assertThat(exit).as(printer.getOutput()).isZero();
        // a route may refer to it, {{camel.server.port}}, even when it is the default port
        assertThat(exportDir.resolve("src/main/resources/application.properties")).content()
                .contains("order.period=1000", "camel.server.port=8080")
                .doesNotContain("camel.component.properties.location");
    }

    @Test
    void propertyOverridesTheKeyOfTheApplicationProperties() throws Exception {
        Path route = writeRoute("shop");
        Files.writeString(dir.resolve("application.properties"), """
                order.period=1000
                camel.server.port=8080
                """);
        // camel run puts the --property values in the run settings
        DependencyResolver overriding = r -> {
            Path settings = r.workDir.resolve(ExportHelper.RUN_SETTINGS_FILE);
            Files.createDirectories(settings.getParent());
            Files.writeString(settings, """
                    camel.main.routesIncludePattern=file:%s
                    camel.server.port=9090
                    dependency=camel:timer
                    """.formatted(r.files.get(0)));
            return 0;
        };
        Path exportDir = dir.resolve("export");
        ExportRequest request = request(route, exportDir);
        request.baseDir = dir;
        request.applicationProperties = new String[] { "camel.server.port=9090" };

        StringPrinter printer = new StringPrinter();
        int exit = new ProjectExporter(new ExportContext().withPrinter(printer).withDependencyResolver(overriding))
                .export(request);

        assertThat(exit).as(printer.getOutput()).isZero();
        assertThat(exportDir.resolve("src/main/resources/application.properties")).content()
                .contains("order.period=1000", "camel.server.port=9090")
                .doesNotContain("camel.server.port=8080");
    }

    @Test
    void exportsInParallelWithWorkDirsOfTheirOwn() throws Exception {
        List<String> before = listWorkDir();

        exportInParallel(false);

        // nothing is written to the .camel-jbang directory of the current directory
        assertThat(listWorkDir()).isEqualTo(before);
    }

    @Test
    void exportsInParallelWithTheDefaultWorkDir() throws Exception {
        List<String> before = listWorkDir();

        try {
            // as camel export does: the .camel-jbang directory, which every export in the directory shares, so they
            // run one at a time
            exportInParallel(true);
        } finally {
            if (before.isEmpty()) {
                PathUtils.deleteDirectory(CamelHome.getWorkDir());
            }
        }
    }

    private void exportInParallel(boolean defaultWorkDir) throws Exception {
        int count = 4;
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Path route = writeRoute("hello" + i);
            ExportRequest request = request(route, dir.resolve("export" + i));
            if (defaultWorkDir) {
                request.workDir = new ExportRequest().workDir;
            }
            tasks.add(() -> new ProjectExporter(
                    new ExportContext().withPrinter(new StringPrinter()).withDependencyResolver(resolver))
                    .export(request));
        }
        ExecutorService executor = Executors.newFixedThreadPool(count);
        try {
            for (Future<Integer> exit : executor.invokeAll(tasks)) {
                assertThat(exit.get()).isZero();
            }
        } finally {
            executor.shutdownNow();
        }

        for (int i = 0; i < count; i++) {
            Path exportDir = dir.resolve("export" + i);
            String route = "hello" + i + ".camel.yaml";
            assertThat(Files.readString(exportDir.resolve("pom.xml"))).contains("<artifactId>hello" + i + "</artifactId>");
            // each project has its own route only
            assertThat(exportDir.resolve("src/main/resources/camel")).isDirectoryContaining("glob:**/" + route)
                    .isDirectoryNotContaining(p -> !p.getFileName().toString().equals(route));
        }
    }

    @ParameterizedTest
    @EnumSource(value = RuntimeType.class, names = { "main", "springBoot", "quarkus" })
    void localJarsAreInTheLibDirectoryAsThePomRefersToThem(RuntimeType runtime) throws Exception {
        Path route = writeRoute("hello");
        // a JAR in a directory that is not built by Maven, and one built by Maven whose file name is not its artifact
        Path greeter = jar(Files.createDirectories(dir.resolve("libs")).resolve("greeter.jar"), null);
        Path util = jar(dir.resolve("util.jar"), "com.acme:acme-util:2.1");
        DependencyResolver withJars = r -> {
            Path settings = r.workDir.resolve(ExportHelper.RUN_SETTINGS_FILE);
            Files.createDirectories(settings.getParent());
            Files.writeString(settings, """
                    camel.main.routesIncludePattern=file:%s
                    camel.jbang.classpathFiles=%s,%s
                    dependency=camel:timer
                    dependency=camel:log
                    """.formatted(route, greeter, util));
            return 0;
        };
        Path exportDir = dir.resolve("export");
        ExportRequest request = request(route, exportDir);
        request.runtime = runtime;

        StringPrinter printer = new StringPrinter();
        int exit = new ProjectExporter(new ExportContext().withPrinter(printer).withDependencyResolver(withJars))
                .export(request);
        assertThat(exit).as(printer.getOutput()).isZero();

        String pom = Files.readString(exportDir.resolve("pom.xml"));
        assertThat(pom).contains("<groupId>local</groupId>", "${project.basedir}/lib/greeter-1.0.jar</systemPath>",
                "<groupId>com.acme</groupId>", "${project.basedir}/lib/acme-util-2.1.jar</systemPath>");
        // the JARs are where the pom.xml refers to them, and only there
        assertThat(exportDir.resolve("lib/greeter-1.0.jar")).hasSameBinaryContentAs(greeter);
        assertThat(exportDir.resolve("lib/acme-util-2.1.jar")).hasSameBinaryContentAs(util);
        try (Stream<Path> files = Files.walk(exportDir)) {
            assertThat(files.filter(p -> p.toString().endsWith(".jar") && !p.toString().contains(".mvn")))
                    .containsExactlyInAnyOrder(exportDir.resolve("lib/greeter-1.0.jar"),
                            exportDir.resolve("lib/acme-util-2.1.jar"));
        }
    }

    /**
     * A JAR with a class, and the pom.properties of Maven when it has a GAV.
     */
    private static Path jar(Path file, String gav) throws Exception {
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(file))) {
            jar.putNextEntry(new JarEntry("acme/Greeter.class"));
            jar.write(new byte[] { (byte) 0xca, (byte) 0xfe });
            jar.closeEntry();
            if (gav != null) {
                String[] ids = gav.split(":");
                jar.putNextEntry(new JarEntry("META-INF/maven/" + ids[0] + "/" + ids[1] + "/pom.properties"));
                jar.write("groupId=%s%nartifactId=%s%nversion=%s%n".formatted(ids[0], ids[1], ids[2])
                        .getBytes(StandardCharsets.UTF_8));
                jar.closeEntry();
            }
        }
        return file;
    }

    @Test
    void doesNotLoadTheKameletCatalogForAnIntegrationWithoutKamelets() throws Exception {
        Path route = writeRoute("hello");
        Path exportDir = dir.resolve("export");
        ExportRequest request = request(route, exportDir);
        // a catalog that cannot be downloaded: the export would fail to load it
        request.kameletsVersion = "0.0.0-no-such-version";

        StringPrinter printer = new StringPrinter();
        int exit = new ProjectExporter(new ExportContext().withPrinter(printer).withDependencyResolver(resolver))
                .export(request);

        assertThat(exit).as(printer.getOutput()).isZero();
        assertThat(exportDir.resolve("pom.xml")).exists();
    }

    @Test
    void failsForTwoFilesWithTheSameNameInDifferentDirectories() throws Exception {
        Path x = writeRoute(Files.createDirectories(dir.resolve("x")), "route", "x");
        Path y = writeRoute(Files.createDirectories(dir.resolve("y")), "route", "y");
        Path exportDir = dir.resolve("export");

        StringPrinter printer = new StringPrinter();
        int exit = new ProjectExporter(new ExportContext().withPrinter(printer).withDependencyResolver(routes(x, y)))
                .export(request(x, exportDir));

        // one of the routes would be lost
        assertThat(exit).isEqualTo(1);
        assertThat(printer.getOutput()).contains(x.toString(), y.toString(),
                "src/main/resources/camel/route.camel.yaml", "rename one of them");
        assertThat(exportDir.resolve("pom.xml")).doesNotExist();
    }

    @Test
    void copiesTheSameFileGivenTwiceOnce() throws Exception {
        Path x = writeRoute(Files.createDirectories(dir.resolve("x")), "route", "x");
        Path exportDir = dir.resolve("export");

        StringPrinter printer = new StringPrinter();
        int exit = new ProjectExporter(
                new ExportContext().withPrinter(printer)
                        .withDependencyResolver(routes(x, dir.resolve("x/../x/route.camel.yaml"))))
                .export(request(x, exportDir));

        assertThat(exit).as(printer.getOutput()).isZero();
        assertThat(exportDir.resolve("src/main/resources/camel/route.camel.yaml")).hasSameTextualContentAs(x);
    }

    /**
     * A resolver whose run loads the given route files.
     */
    private static DependencyResolver routes(Path... files) {
        return r -> {
            Path settings = r.workDir.resolve(ExportHelper.RUN_SETTINGS_FILE);
            Files.createDirectories(settings.getParent());
            String pattern = Stream.of(files).map(f -> "file:" + f).collect(Collectors.joining(","));
            Files.writeString(settings, """
                    camel.main.routesIncludePattern=%s
                    dependency=camel:timer
                    dependency=camel:log
                    """.formatted(pattern));
            return 0;
        };
    }

    @Test
    void requiresRuntime() throws Exception {
        ExportRequest request = new ExportRequest();
        request.files.add(dir.resolve("missing.camel.yaml").toString());

        StringPrinter printer = new StringPrinter();
        int exit = new ProjectExporter(new ExportContext().withPrinter(printer).withDependencyResolver(resolver))
                .export(request);

        assertThat(exit).isEqualTo(1);
        assertThat(printer.getOutput()).contains("The runtime option must be specified");
        assertThat(resolved).isEmpty();
    }

    @Test
    void failsOnMissingFile() throws Exception {
        ExportRequest request = new ExportRequest();
        request.files.add(dir.resolve("missing.camel.yaml").toString());
        request.runtime = RuntimeType.main;

        StringPrinter printer = new StringPrinter();
        int exit = new ProjectExporter(new ExportContext().withPrinter(printer).withDependencyResolver(resolver))
                .export(request);

        // as camel run: exit 66 and the message on stderr; it was 1 on stdout (CAMEL-26931)
        assertThat(exit).isEqualTo(RunPipeline.FILE_NOT_FOUND);
        assertThat(printer.getStderr()).contains("Path does not exist");
        assertThat(printer.getStdout()).isEmpty();
        assertThat(resolved).isEmpty();
    }

    private ExportContext exportContext() {
        return new ExportContext().withPrinter(new StringPrinter()).withDependencyResolver(resolver);
    }

    @Test
    void replacesTheDirectoryOfAnEarlierExport() throws Exception {
        Path exportDir = dir.resolve("export");
        assertThat(export(request(writeRoute("first"), exportDir))).isZero();
        assertThat(exportDir.resolve(ExportHelper.EXPORT_MARKER)).exists();

        // the export replaces the earlier one, so no stale files of it are left
        assertThat(export(request(writeRoute("second"), exportDir))).isZero();
        assertThat(exportDir.resolve("src/main/resources/camel")).isDirectoryContaining("glob:**/second.camel.yaml")
                .isDirectoryNotContaining("glob:**/first.camel.yaml");
        assertThat(exportDir.resolve(ExportHelper.EXPORT_MARKER)).exists();
    }

    @Test
    void keepsTheFilesOfADirectoryThatNoExportCreated() throws Exception {
        Path exportDir = Files.createDirectories(dir.resolve("export"));
        Path todo = Files.writeString(exportDir.resolve("TODO.txt"), "my notes");
        Path notes = Files.writeString(Files.createDirectories(exportDir.resolve("docs")).resolve("notes.txt"), "more");

        StringPrinter printer = new StringPrinter();
        int exit = new ProjectExporter(new ExportContext().withPrinter(printer).withDependencyResolver(resolver))
                .export(request(writeRoute("hello"), exportDir));

        assertThat(exit).isEqualTo(1);
        assertThat(printer.getOutput()).contains("Cannot export to " + exportDir.toAbsolutePath(), "--clean-dir");
        assertThat(todo).hasContent("my notes");
        assertThat(notes).hasContent("more");
        assertThat(exportDir.resolve("pom.xml")).doesNotExist();
        // nothing was resolved: the export stops before it boots the integration
        assertThat(resolved).isEmpty();

        // unless they are deleted on purpose
        ExportRequest request = request(writeRoute("hello"), exportDir);
        request.cleanExportDir = true;
        request.yes = true;
        assertThat(export(request)).isZero();
        assertThat(todo).doesNotExist();
        assertThat(exportDir.resolve("pom.xml")).exists();
    }

    @Test
    void exportsToADirectoryWithOnlyHiddenFiles() throws Exception {
        // such as a clone of an empty Git repository
        Path exportDir = Files.createDirectories(dir.resolve("export"));
        Path git = Files.writeString(Files.createDirectories(exportDir.resolve(".git")).resolve("HEAD"), "ref");

        assertThat(export(request(writeRoute("hello"), exportDir))).isZero();
        assertThat(exportDir.resolve("pom.xml")).exists();
        assertThat(git).hasContent("ref");
    }

    private int export(ExportRequest request) throws Exception {
        StringPrinter printer = new StringPrinter();
        int exit = new ProjectExporter(new ExportContext().withPrinter(printer).withDependencyResolver(resolver))
                .export(request);
        assertThat(exit).as(printer.getOutput()).isNotNegative();
        return exit;
    }

    private Path writeRoute(String name) throws Exception {
        return writeRoute(dir, name, name);
    }

    private static Path writeRoute(Path directory, String name, String timer) throws Exception {
        Path route = directory.resolve(name + ".camel.yaml");
        Files.writeString(route, """
                - from:
                    uri: timer:%s
                    steps:
                      - to: log:%s
                """.formatted(timer, timer));
        return route;
    }

    private ExportRequest request(Path route, Path exportDir) {
        ExportRequest request = new ExportRequest();
        request.files.add(route.toString());
        request.runtime = RuntimeType.main;
        request.exportDir = exportDir.toString();
        request.workDir = exportDir.resolveSibling(exportDir.getFileName() + "-work");
        request.mavenWrapper = false;
        request.quiet = true;
        return request;
    }

    private static List<String> listWorkDir() throws Exception {
        Path work = CamelHome.getWorkDir();
        if (!Files.exists(work)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(work)) {
            return files.map(p -> p + ":" + p.toFile().lastModified()).sorted().toList();
        }
    }

    @Test
    @SetSystemProperty(key = "camel.extra.repos", value = "sysprop=https://sysprop.example.com/maven2")
    void extraReposOfTheRequestInThePom() throws Exception {
        Path exportDir = dir.resolve("export");
        ExportRequest request = request(writeRoute("hello"), exportDir);
        request.mavenResolver = new MavenResolver(true, null, false, "explicit=https://explicit.example.com/maven2");

        assertThat(new ProjectExporter(exportContext()).export(request)).isZero();

        // the extra repositories of the request win over the system property
        assertThat(Files.readString(exportDir.resolve("pom.xml")))
                .contains("<url>https://explicit.example.com/maven2</url>")
                .doesNotContain("sysprop.example.com");
    }

    @Test
    @SetSystemProperty(key = "camel.extra.repos", value = "sysprop=https://sysprop.example.com/maven2")
    void noExtraRepos() throws Exception {
        Path exportDir = dir.resolve("export");
        ExportRequest request = request(writeRoute("hello"), exportDir);
        request.mavenResolver = new MavenResolver(true, null, false, "");

        assertThat(new ProjectExporter(exportContext()).export(request)).isZero();

        assertThat(Files.readString(exportDir.resolve("pom.xml"))).doesNotContain("sysprop.example.com");
    }

    @Test
    @SetSystemProperty(key = "camel.extra.repos", value = "sysprop=https://sysprop.example.com/maven2")
    void extraReposOfTheSystemProperty() throws Exception {
        Path exportDir = dir.resolve("export");
        ExportRequest request = request(writeRoute("hello"), exportDir);

        assertThat(new ProjectExporter(exportContext()).export(request)).isZero();

        // as before the request had extra repositories
        assertThat(Files.readString(exportDir.resolve("pom.xml"))).contains("<url>https://sysprop.example.com/maven2</url>");
    }

    @Test
    @SetSystemProperty(key = "camel.jbang.camelSpringBootVersion", value = "9.9.9")
    @SetSystemProperty(key = "camel.jbang.quarkusVersion", value = "9.9.9")
    @SetSystemProperty(key = "camel.jbang.quarkus.platform.url", value = "https://registry.example.com/client/platforms")
    void prepareReadsNoSystemProperties() throws Exception {
        ExportRequest request = new ExportRequest();
        request.baseDir = dir;

        new ProjectExporter(new ExportContext().withPrinter(new StringPrinter()).withDependencyResolver(resolver))
                .prepare(request);

        // the CLI sets what the system properties configure on the request
        assertThat(request.camelSpringBootVersion).isNull();
        assertThat(request.quarkusPlatform.quarkusVersion()).isNull();
        assertThat(request.quarkusPlatform.quarkusExtensionRegistryBaseUri())
                .isEqualTo(QuarkusPlatform.extensionRegistryBaseUri(RuntimeType.QUARKUS_EXTENSION_REGISTRY_BASE_URL));
    }

    @Test
    void projectNameAndVersion() {
        assertThat(ProjectExporter.projectName("foo", "com.acme:bar:2.0", List.of("baz.yaml"))).isEqualTo("foo");
        assertThat(ProjectExporter.projectName(null, "com.acme:bar:2.0", List.of("baz.yaml"))).isEqualTo("bar");
        assertThat(ProjectExporter.projectName(null, null, List.of("routes/baz.camel.yaml"))).isEqualTo("baz");
        assertThat(ProjectExporter.projectName(null, null, List.of())).isNull();
        assertThat(ProjectExporter.projectVersion("com.acme:bar:2.0")).isEqualTo("2.0");
        assertThat(ProjectExporter.projectVersion(null)).isEqualTo("1.0-SNAPSHOT");
    }
}
