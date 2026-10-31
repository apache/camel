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
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.run.ConfiguredRun;
import org.apache.camel.dsl.jbang.run.RunContext;
import org.apache.camel.dsl.jbang.run.common.ExportHelper;
import org.apache.camel.dsl.jbang.run.common.PathUtils;
import org.apache.camel.dsl.jbang.run.common.RuntimeType;
import org.apache.camel.main.KameletMain;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Golden tests of what camel run and camel export make of their input: the run settings that the run writes (and the
 * export reads), the properties that the run gives Camel Main, and the project that the export creates. They pin the
 * behaviour that moves when the settings pipeline of camel run is shared with the export (CAMEL-24727), so the expected
 * files are written from the code before that change, and must not change with it.
 * <p>
 * Each case is a directory of src/test/resources/golden with the files to run, and the expected output is in
 * src/test/resources/golden/expected. Run with -Dcamel.golden.update=true to write the expected files again, and review
 * the difference.
 */
@UsesWorkingDirectory
class RunExportGoldenTest {

    private static final Path GOLDEN = Path.of("src/test/resources/golden");
    private static final Path EXPECTED = GOLDEN.resolve("expected");
    private static final boolean UPDATE = Boolean.getBoolean("camel.golden.update");

    // the camel system properties of the JVM, which the commands read (such as camel.jbang.repos): a case runs without
    // them, so a property that another test left set does not change the output
    private final Map<String, String> camelProperties = new TreeMap<>();

    @BeforeEach
    void clearCamelSystemProperties() {
        for (String key : System.getProperties().stringPropertyNames()) {
            if (key.startsWith("camel.") && !key.equals("camel.golden.update")) {
                camelProperties.put(key, System.getProperty(key));
                System.clearProperty(key);
            }
        }
    }

    @AfterEach
    void restoreCamelSystemProperties() {
        camelProperties.forEach(System::setProperty);
    }

    /**
     * A case: the files and the options of the command, and whether it is checked for camel export, camel run, or both.
     */
    record Case(String name, List<String> files, List<String> args, boolean export, boolean run) {
        @Override
        public String toString() {
            return name;
        }
    }

    static Stream<Case> cases() {
        return Stream.of(
                both("yaml", files("yaml/hello.camel.yaml")),
                both("xml", files("xml/hello.camel.xml")),
                both("java", files("java/Hello.java")),
                // a directory: every kind of file it can have (routes, beans, properties, readme, scripts, tls, jkube)
                both("mixed", files("mixed")),
                both("groovy", files("groovy")),
                both("profile", files("profile"), "--profile=dev"),
                // a component that only the prod profile uses: the export boots with the prod profile to find it
                both("profile-prod", files("profileprod"), "--profile=prod"),
                both("kamelet", files("kamelet/route.camel.yaml", "kamelet/greeting-source.kamelet.yaml")),
                both("local-kamelet-dir", files("localkamelet/route.camel.yaml"),
                        "--local-kamelet-dir=" + GOLDEN.resolve("localkamelet/kamelets")),
                both("deps", files("deps/Deps.java"), "--dep=camel:jackson,org.example:extra:1.0"),
                both("yaml-deps", files("yamldeps/deps.camel.yaml")),
                both("openapi", files("openapi/greeting.camel.yaml"), "--open-api=" + GOLDEN.resolve("openapi/api.yaml")),
                both("resource-dir", files("resourcedir/hello.camel.yaml"),
                        "--resource-dir=" + GOLDEN.resolve("resourcedir/schemas")),
                both("excludes", files("excludes"), "--exclude=*.xml"),
                both("options", files("options/hello.camel.yaml"), "--name=golden-options", "--port=8181",
                        "--management-port=9191", "--observe", "--console", "--gav=com.acme:golden:2.0"),
                // camel run of a pom.xml runs the Maven project in its own JVM
                new Case("pom-project", files("pomproject/pom.xml"), List.of(), true, false),
                // how camel run starts (in the background, with a debugger) changes nothing in an export
                new Case("run-settings", files("runsettings"), List.of(), true, false),
                // the options of camel run only
                run("run-options", files("options/hello.camel.yaml"),
                        "--properties=" + GOLDEN.resolve("options/extra.properties"), "--prop=foo=bar",
                        "--lazy-bean=false", "--jfr", "--trace", "--backlog-trace", "--max-messages=5",
                        "--max-seconds=60", "--logging-level=debug", "--logging-category=org.example=trace"),
                run("dev", files("yaml/hello.camel.yaml", "groovy/Greeting.groovy"), "--dev"),
                run("source-dir", List.of(), "--source-dir=" + GOLDEN.resolve("sourcedir/routes")),
                run("code", List.of(), "--code=from(\"timer:code\").to(\"log:code\")"),
                run("stub-all", files("yaml/hello.camel.yaml"), "--stub=all"),
                run("stub-names", files("yaml/hello.camel.yaml"), "--stub=log,timer:hello"),
                run("chaos", files("yaml/hello.camel.yaml"), "--chaos"));
    }

    static Stream<Case> exportCases() {
        return cases().filter(Case::export);
    }

    static Stream<Case> runCases() {
        return cases().filter(Case::run);
    }

    private static List<String> files(String... files) {
        return List.of(files);
    }

    private static Case both(String name, List<String> files, String... args) {
        return new Case(name, files, List.of(args), true, true);
    }

    private static Case run(String name, List<String> files, String... args) {
        return new Case(name, files, List.of(args), false, true);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("exportCases")
    void export(Case c, @TempDir Path out) throws Exception {
        export(c, out, new Export(new CamelJBangMain()));
    }

    private void export(Case c, Path out, Export command) throws Exception {
        verify(c.name() + ".export.txt", exportOutput(c, out, command));
    }

    /**
     * camel.jbang.background and camel.jbang.jvmDebug in application.properties only say how camel run starts: the
     * export boots the integration in its own JVM all the same, and gives the same project as without them.
     */
    @Test
    void exportIgnoresHowCamelRunStarts(@TempDir Path dir) throws Exception {
        Path with = copyFixture("runsettings", dir.resolve("with"));
        Path without = copyFixture("runsettings", dir.resolve("without"));
        Path props = without.resolve("application.properties");
        Files.writeString(props, Files.readString(props).replaceAll("(?m)^camel\\.jbang\\.(background|jvmDebug)=.*\\R", ""));

        String withOutput = exportOutput(new Case("run-settings", files(with.toString()), List.of(), true, false),
                dir.resolve("with-out"), new Export(new CamelJBangMain())).replace(with.toString(), "${fixture}");
        String withoutOutput = exportOutput(new Case("run-settings", files(without.toString()), List.of(), true, false),
                dir.resolve("without-out"), new Export(new CamelJBangMain())).replace(without.toString(), "${fixture}");

        assertThat(Files.readString(props)).doesNotContain("camel.jbang.background", "camel.jbang.jvmDebug");
        assertThat(withOutput).isEqualTo(withoutOutput);
    }

    private static Path copyFixture(String name, Path target) throws Exception {
        Files.createDirectories(target);
        try (Stream<Path> files = Files.list(GOLDEN.resolve(name))) {
            for (Path f : files.toList()) {
                Files.copy(f, target.resolve(f.getFileName()));
            }
        }
        return target.toRealPath();
    }

    private String exportOutput(Case c, Path out, Export command) throws Exception {
        List<String> args = new ArrayList<>(
                List.of("--runtime=main", "--quiet", "--dir=" + out, CamelCommandBaseTestSupport.quarkusExtRegistry()));
        if (c.args().stream().noneMatch(a -> a.startsWith("--gav="))) {
            args.add("--gav=org.example.golden:" + c.name() + ":1.0.0");
        }
        args.addAll(c.args());
        CommandLine.populateCommand(command, args.toArray(new String[0]));
        command.files = c.files().stream().map(f -> GOLDEN.resolve(f).toString()).collect(Collectors.toList());

        assertThat(command.doCall()).isZero();

        StringBuilder sb = new StringBuilder();
        sb.append("# run settings\n");
        sb.append(settings(CommandLineHelper.getWorkDir().resolve(ExportHelper.RUN_SETTINGS_FILE)));
        sb.append(project(out));
        return normalize(sb.toString(), out);
    }

    /**
     * The files of an exported project, its pom.xml and its application.properties.
     */
    private static String project(Path dir) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("\n# project files\n");
        try (Stream<Path> files = Files.walk(dir)) {
            files.filter(Files::isRegularFile).map(p -> dir.relativize(p).toString().replace('\\', '/')).sorted()
                    .forEach(p -> sb.append(p).append('\n'));
        }
        sb.append("\n# pom.xml\n").append(pom(dir.resolve("pom.xml")));
        sb.append("\n# application.properties\n");
        Path props = dir.resolve("src/main/resources/application.properties");
        if (Files.exists(props)) {
            sb.append(Files.readString(props));
        }
        return sb.toString();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("runCases")
    void run(Case c, @TempDir Path work) throws Exception {
        CapturingRun run = new CapturingRun();
        // the Quarkus platform of the local registry, as the export (not the one online)
        List<String> args = new ArrayList<>(List.of(CamelCommandBaseTestSupport.quarkusExtRegistry()));
        args.addAll(c.args());
        CommandLine.populateCommand(run, args.toArray(new String[0]));
        run.files = c.files().stream().map(f -> GOLDEN.resolve(f).toString()).collect(Collectors.toList());
        run.workDir = work;

        assertThat(run.doCall()).isZero();
        assertThat(run.main).as("the run gets to start Camel").isNotNull();

        StringBuilder sb = new StringBuilder();
        sb.append("# run settings\n").append(settings(work.resolve(ExportHelper.RUN_SETTINGS_FILE)));
        sb.append("\n# initial properties\n").append(sorted(run.main.getInitialProperties()));
        sb.append("\n# override properties\n").append(sorted(run.main.getOverrideProperties()));
        verify(c.name() + ".run.txt", normalize(sb.toString(), work));
    }

    /**
     * Runs as camel run does up to starting Camel, and keeps the Camel Main it would start.
     */
    static class CapturingRun extends Run {

        KameletMain main;

        CapturingRun() {
            super(new CamelJBangMain());
        }

        @Override
        protected int runKameletMain(ConfiguredRun run) {
            this.main = run.getMain();
            return 0;
        }
    }

    /**
     * A run with Maven (--runtime=main, quarkus or spring-boot, or a Maven project): the commands it starts, and the
     * project it exports to run.
     */
    record MavenCase(String name, List<String> files, List<String> args, boolean copy) {
        @Override
        public String toString() {
            return name;
        }
    }

    static Stream<MavenCase> mavenCases() {
        return Stream.of(
                new MavenCase("maven-main", files("yaml/hello.camel.yaml"), List.of("--runtime=main"), false),
                new MavenCase(
                        "maven-main-mixed", files("mixed"), List.of("--runtime=main", "--dev", "--profile=dev", "--jfr"),
                        false),
                new MavenCase("maven-quarkus", files("yaml/hello.camel.yaml"), List.of("--runtime=quarkus"), false),
                new MavenCase(
                        "maven-quarkus-dev", files("yaml/hello.camel.yaml"),
                        List.of("--runtime=quarkus", "--dev", "--console"), false),
                // a released Camel, as the catalog of camel-spring-boot is not built with this one
                new MavenCase(
                        "maven-spring-boot", files("yaml/hello.camel.yaml"),
                        List.of("--runtime=spring-boot", "--camel-version=4.13.0"), false),
                // the project is copied, as the run writes its files next to the pom.xml
                new MavenCase("maven-project", files("pomproject/pom.xml"), List.of(), true),
                // camel run --chaos adds camel-chaos to the exported project
                new MavenCase(
                        "maven-main-chaos", files("yaml/hello.camel.yaml"), List.of("--runtime=main", "--chaos"), false));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("mavenCases")
    void runWithMaven(MavenCase c, @TempDir Path dir) throws Exception {
        List<String> files = new ArrayList<>();
        for (String f : c.files()) {
            Path source = GOLDEN.resolve(f);
            if (c.copy()) {
                Path copy = dir.resolve("project");
                PathUtils.copyDirectory(source.getParent(), copy);
                files.add(copy.resolve(source.getFileName()).toString());
            } else {
                files.add(source.toString());
            }
        }
        MavenRun run = new MavenRun();
        List<String> args = new ArrayList<>(List.of(CamelCommandBaseTestSupport.quarkusExtRegistry()));
        args.addAll(c.args());
        CommandLine.populateCommand(run, args.toArray(new String[0]));
        run.files = files;
        run.workDir = dir.resolve("work");

        assertThat(run.doCall()).isZero();
        assertThat(run.started).as("the run starts Maven").isNotEmpty();

        StringBuilder sb = new StringBuilder();
        Path runDir = null;
        for (ProcessBuilder pb : run.started) {
            sb.append("# command\n").append(String.join("\n", pb.command())).append('\n');
            if (pb.directory() != null) {
                sb.append("directory: ").append(pb.directory()).append('\n');
            }
            String mavenOpts = pb.environment().get("MAVEN_OPTS");
            if (mavenOpts != null && !mavenOpts.equals(System.getenv("MAVEN_OPTS"))) {
                sb.append("MAVEN_OPTS: ").append(mavenOpts).append('\n');
            }
            sb.append('\n');
            if (runDir == null) {
                runDir = MavenRun.exportedProject(pb);
            }
        }
        String text = sb.toString();
        if (runDir != null) {
            // the project exported to run
            text = text + project(runDir);
            text = text.replace(runDir.toRealPath().toString(), "${run-dir}").replace(runDir.toAbsolutePath().toString(),
                    "${run-dir}");
            // removed when the application ends, which a run of this test does not start
            PathUtils.deleteDirectory(runDir);
        }
        text = text.replace(System.getProperty("java.home"), "${java.home}");
        // the run directory, as the relative path of the Maven wrapper (it is named after the time of the run)
        text = text.replaceAll("\\.camel-jbang-run/\\d+", Matcher.quoteReplacement("${run-dir}"));
        verify(c.name() + ".maven.txt", normalize(text, dir));
    }

    /**
     * Runs as camel run with Maven does, but starts no process: Maven and the application are taken as done.
     */
    static class MavenRun extends Run {

        final List<ProcessBuilder> started = new ArrayList<>();

        MavenRun() {
            super(new CamelJBangMain());
        }

        @Override
        protected RunContext runContext() {
            return super.runContext().withProcessStarter(this::start);
        }

        private Process start(ProcessBuilder pb) throws IOException {
            started.add(pb);
            Path project = exportedProject(pb);
            if (project != null && pb.command().contains("package") && pb.command().contains("-DskipTests")
                    && pb.command().stream().noneMatch(a -> a.startsWith("quarkus:") || a.startsWith("spring-boot:"))) {
                // the runner JAR that the build of a Camel Main project makes
                Files.createDirectories(project.resolve("target"));
                Files.writeString(project.resolve("target/runner.jar"), "");
            }
            return new DoneProcess();
        }

        /**
         * The exported project that a Maven command builds (--file run-dir/pom.xml), or null.
         */
        static Path exportedProject(ProcessBuilder pb) {
            List<String> cmd = pb.command();
            int i = cmd.indexOf("--file");
            if (i >= 0 && i + 1 < cmd.size() && cmd.get(i + 1).endsWith("pom.xml")) {
                Path pom = Path.of(cmd.get(i + 1));
                if (pom.getFileName().toString().equals("pom.xml")) {
                    return pom.getParent();
                }
            }
            return null;
        }
    }

    /**
     * A process that has ended successfully.
     */
    static class DoneProcess extends Process {

        @Override
        public OutputStream getOutputStream() {
            return OutputStream.nullOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return InputStream.nullInputStream();
        }

        @Override
        public InputStream getErrorStream() {
            return InputStream.nullInputStream();
        }

        @Override
        public int waitFor() {
            return 0;
        }

        @Override
        public int exitValue() {
            return 0;
        }

        @Override
        public void destroy() {
            // ended already
        }

        @Override
        public long pid() {
            // not 0, which Debug reads as no process started
            return -1;
        }
    }

    private static String settings(Path file) throws Exception {
        return Files.exists(file) ? Files.readString(file) : "";
    }

    private static String sorted(Properties props) {
        Map<String, String> map = new TreeMap<>();
        if (props == null) {
            return "";
        }
        props.forEach((k, v) -> map.put(k.toString(), v.toString()));
        StringBuilder sb = new StringBuilder();
        map.forEach((k, v) -> sb.append(k).append('=').append(v).append('\n'));
        return sb.toString();
    }

    private static String pom(Path pom) throws Exception {
        Model model;
        try (Reader reader = Files.newBufferedReader(pom)) {
            model = new MavenXpp3Reader().read(reader);
        }
        StringBuilder sb = new StringBuilder();
        sb.append(model.getGroupId()).append(':').append(model.getArtifactId()).append(':').append(model.getVersion())
                .append('\n');
        sb.append("properties: ").append(new TreeMap<>(model.getProperties()).keySet()).append('\n');
        for (Dependency d : model.getDependencies()) {
            sb.append("dependency ").append(d.getGroupId()).append(':').append(d.getArtifactId());
            // only the versions of the files of the case, not the ones the build brings (such as log4j)
            if (d.getVersion() != null && d.getGroupId().matches("org\\.example.*|org\\.apache\\.commons")) {
                sb.append(':').append(d.getVersion());
            }
            if (d.getScope() != null) {
                sb.append(" (").append(d.getScope()).append(')');
            }
            sb.append('\n');
        }
        model.getRepositories().forEach(r -> sb.append("repository ").append(r.getUrl()).append('\n'));
        model.getBuild().getPlugins()
                .forEach(p -> sb.append("plugin ").append(p.getGroupId()).append(':').append(p.getArtifactId())
                        .append('\n'));
        return sb.toString();
    }

    /**
     * Replaces what differs from one machine or build to another: the directories, and the versions that the build
     * brings (of Camel, Spring Boot and the Kamelets), so that a version upgrade does not change the expected output.
     */
    private static String normalize(String text, Path dir) throws Exception {
        String answer = text;
        Map<String, String> replacements = new LinkedHashMap<>();
        replacements.put(dir.toRealPath().toString(), "${dir}");
        replacements.put(dir.toAbsolutePath().toString(), "${dir}");
        replacements.put(Path.of("").toAbsolutePath().toString(), "${basedir}");
        replacements.put(System.getProperty("user.home"), "${user.home}");
        for (Map.Entry<String, String> e : replacements.entrySet()) {
            // as is, and as escaped in a properties file
            answer = answer.replace(e.getKey(), e.getValue())
                    .replace(e.getKey().replace(":", "\\:"), e.getValue());
        }
        // a version is replaced where it is a whole value, not inside another one
        answer = replaceVersion(answer, new DefaultCamelCatalog().getCatalogVersion(), "${camel.version}");
        answer = replaceVersion(answer, RuntimeType.SPRING_BOOT_VERSION, "${spring-boot.version}");
        answer = replaceVersion(answer, RuntimeType.KAMELETS_VERSION, "${kamelets.version}");
        return sortFileLists(answer);
    }

    /**
     * Sorts the lists of files (such as camel.jbang.classpathFiles and camel.main.routesIncludePattern): a directory
     * given to camel run is listed in the order of the file system, which differs from one machine to another.
     */
    private static String sortFileLists(String text) {
        Matcher m = Pattern.compile("(?m)^(\\s*(?:camel\\.jbang\\.\\w+Files|camel\\.main\\.routesIncludePattern)=)(.*)$")
                .matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String files = Arrays.stream(m.group(2).split(",")).sorted().collect(Collectors.joining(","));
            m.appendReplacement(sb, Matcher.quoteReplacement(m.group(1) + files));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String replaceVersion(String text, String version, String name) {
        return text.replaceAll("(?m)(?<=[=:])" + Pattern.quote(version) + "(?=$|[\\s,])", Matcher.quoteReplacement(name));
    }

    private static void verify(String name, String actual) throws Exception {
        Path expected = EXPECTED.resolve(name);
        if (UPDATE || !Files.exists(expected)) {
            Files.createDirectories(EXPECTED);
            Files.writeString(expected, actual);
            if (!UPDATE) {
                throw new AssertionError("No expected output yet, written to " + expected + ": review and commit it");
            }
            return;
        }
        assertThat(actual).as("%s (run with -Dcamel.golden.update=true to update)", name)
                .isEqualTo(Files.readString(expected));
    }
}
