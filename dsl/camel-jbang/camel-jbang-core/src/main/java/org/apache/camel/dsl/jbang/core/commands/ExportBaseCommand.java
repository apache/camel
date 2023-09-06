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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.Stack;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.common.RuntimeCompletionCandidates;
import org.apache.camel.dsl.jbang.core.common.RuntimeUtil;
import org.apache.camel.dsl.jbang.core.common.VersionHelper;
import org.apache.camel.tooling.maven.MavenGav;
import org.apache.camel.util.CamelCaseOrderedProperties;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StringHelper;
import picocli.CommandLine;

abstract class ExportBaseCommand extends CamelCommand {

    protected static final String BUILD_DIR = ".camel-jbang/work";

    protected static final String[] SETTINGS_PROP_SOURCE_KEYS = new String[] {
            "camel.main.routesIncludePattern",
            "camel.component.properties.location",
            "camel.component.kamelet.location",
            "camel.jbang.classpathFiles",
            "camel.jbang.localKameletDir",
            "camel.jbang.jkubeFiles"
    };

    private static final Pattern PACKAGE_PATTERN = Pattern.compile(
            "^\\s*package\\s+([a-zA-Z][.\\w]*)\\s*;.*$", Pattern.MULTILINE);

    @CommandLine.Parameters(description = "The Camel file(s) to export. If no files is specified then what was last run will be exported.",
                            arity = "0..9", paramLabel = "<files>", parameterConsumer = FilesConsumer.class)
    protected Path[] filePaths; // Defined only for file path completion; the field never used

    protected List<String> files = new ArrayList<>();

    @CommandLine.Option(names = { "--profile" }, scope = CommandLine.ScopeType.INHERIT, defaultValue = "application",
                        description = "Profile to use, which refers to loading properties file with the given profile name. By default application.properties is loaded.")
    protected String profile;

    @CommandLine.Option(names = { "--repos" },
                        description = "Additional maven repositories (Use commas to separate multiple repositories)")
    protected String repos;

    @CommandLine.Option(names = {
            "--dep", "--deps" }, description = "Add additional dependencies (Use commas to separate multiple dependencies).")
    protected String dependencies;

    @CommandLine.Option(names = { "--runtime" }, completionCandidates = RuntimeCompletionCandidates.class,
                        description = "Runtime (spring-boot, quarkus, or camel-main)")
    protected String runtime;

    @CommandLine.Option(names = { "--gav" }, description = "The Maven group:artifact:version")
    protected String gav;

    @CommandLine.Option(names = { "--main-classname" },
                        description = "The class name of the Camel Main application class",
                        defaultValue = "CamelApplication")
    protected String mainClassname;

    @CommandLine.Option(names = { "--java-version" }, description = "Java version", defaultValue = "17")
    protected String javaVersion;

    @CommandLine.Option(names = { "--camel-version" },
                        description = "To export using a different Camel version than the default version.")
    protected String camelVersion;

    @CommandLine.Option(names = {
            "--kamelets-version" }, description = "Apache Camel Kamelets version")
    protected String kameletsVersion;

    @CommandLine.Option(names = { "--local-kamelet-dir" },
                        description = "Local directory for loading Kamelets (takes precedence)")
    protected String localKameletDir;

    @CommandLine.Option(names = { "--spring-boot-version" }, description = "Spring Boot version",
                        defaultValue = "3.1.2")
    protected String springBootVersion;

    @CommandLine.Option(names = { "--camel-spring-boot-version" }, description = "Camel version to use with Spring Boot")
    protected String camelSpringBootVersion;

    @CommandLine.Option(names = { "--quarkus-group-id" }, description = "Quarkus Platform Maven groupId",
                        defaultValue = "io.quarkus.platform")
    protected String quarkusGroupId;

    @CommandLine.Option(names = { "--quarkus-artifact-id" }, description = "Quarkus Platform Maven artifactId",
                        defaultValue = "quarkus-bom")
    protected String quarkusArtifactId;

    @CommandLine.Option(names = { "--quarkus-version" }, description = "Quarkus Platform version",
                        defaultValue = "3.2.5.Final")
    protected String quarkusVersion;

    @CommandLine.Option(names = { "--maven-wrapper" }, defaultValue = "true",
                        description = "Include Maven Wrapper files in exported project")
    protected boolean mavenWrapper;

    @CommandLine.Option(names = { "--gradle-wrapper" }, defaultValue = "true",
                        description = "Include Gradle Wrapper files in exported project")
    protected boolean gradleWrapper;

    @CommandLine.Option(names = { "--build-tool" }, defaultValue = "maven",
                        description = "Build tool to use (maven or gradle)")
    protected String buildTool;

    @CommandLine.Option(names = { "--open-api" }, description = "Adds an OpenAPI spec from the given file (json or yaml file)")
    protected String openapi;

    @CommandLine.Option(names = {
            "--dir",
            "--directory" }, description = "Directory where the project will be exported", defaultValue = ".")
    protected String exportDir;

    @CommandLine.Option(names = { "--logging-level" }, defaultValue = "info", description = "Logging level")
    protected String loggingLevel;

    @CommandLine.Option(names = { "--package-name" },
                        description = "For Java source files should they have the given package name. By default the package name is computed from the Maven GAV. "
                                      +
                                      "Use false to turn off and not include package name in the Java source files.")
    protected String packageName;

    @CommandLine.Option(names = { "--fresh" }, description = "Make sure we use fresh (i.e. non-cached) resources")
    protected boolean fresh;

    @CommandLine.Option(names = { "--additional-properties" },
                        description = "Additional maven properties, ex. --additional-properties=prop1=foo,prop2=bar")
    protected String additionalProperties;

    @CommandLine.Option(names = { "--secrets-refresh" }, defaultValue = "false", description = "Enabling secrets refresh")
    protected boolean secretsRefresh;

    @CommandLine.Option(names = { "--secrets-refresh-providers" },
                        description = "Comma separated list of providers in the set aws,gcp and azure, to use in combination with --secrets-refresh option")
    protected String secretsRefreshProviders;

    @CommandLine.Option(names = { "--logging" }, defaultValue = "false",
                        description = "Can be used to turn on logging (logs to file in <user home>/.camel directory)")
    boolean logging;

    @CommandLine.Option(names = { "--quiet" }, defaultValue = "false",
                        description = "Will be quiet, only print when error occurs")
    boolean quiet;

    public ExportBaseCommand(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        // configure logging first
        if (logging) {
            RuntimeUtil.configureLog(loggingLevel, false, false, false, true);
        } else {
            RuntimeUtil.configureLog("off", false, false, false, true);
        }

        if (!quiet) {
            printConfigurationValues("Exporting integration with the following configuration:");
        }
        // export
        return export();
    }

    public String getProfile() {
        return profile;
    }

    protected static String mavenRepositoriesAsPomXml(String repos) {
        StringBuilder sb = new StringBuilder();
        int i = 1;
        sb.append("    <repositories>\n");
        for (String repo : repos.split(",")) {
            sb.append("        <repository>\n");
            sb.append("            <id>custom").append(i++).append("</id>\n");
            sb.append("            <url>").append(repo).append("</url>\n");
            if (repo.contains("snapshots")) {
                sb.append("            <releases>\n");
                sb.append("                <enabled>false</enabled>\n");
                sb.append("            </releases>\n");
                sb.append("            <snapshots>\n");
                sb.append("                <enabled>true</enabled>\n");
                sb.append("            </snapshots>\n");
            }
            sb.append("        </repository>\n");
        }
        sb.append("    </repositories>\n");
        sb.append("    <pluginRepositories>\n");
        for (String repo : repos.split(",")) {
            sb.append("        <pluginRepository>\n");
            sb.append("            <id>custom").append(i++).append("</id>\n");
            sb.append("            <url>").append(repo).append("</url>\n");
            if (repo.contains("snapshots")) {
                sb.append("            <releases>\n");
                sb.append("                <enabled>false</enabled>\n");
                sb.append("            </releases>\n");
                sb.append("            <snapshots>\n");
                sb.append("                <enabled>true</enabled>\n");
                sb.append("            </snapshots>\n");
            }
            sb.append("        </pluginRepository>\n");
        }
        sb.append("    </pluginRepositories>\n");
        return sb.toString();
    }

    protected abstract Integer export() throws Exception;

    protected static String getScheme(String name) {
        int pos = name.indexOf(":");
        if (pos != -1) {
            return name.substring(0, pos);
        }
        return null;
    }

    protected Integer runSilently() throws Exception {
        Run run = new Run(getMain());
        // need to declare the profile to use for run
        run.profile = profile;
        run.localKameletDir = localKameletDir;
        run.dependencies = dependencies;
        run.files = files;
        run.openapi = openapi;
        return run.runSilent();
    }

    protected Set<String> resolveDependencies(File settings, File profile) throws Exception {
        Set<String> answer = new TreeSet<>((o1, o2) -> {
            // favour org.apache.camel first
            boolean c1 = o1.contains("org.apache.camel:");
            boolean c2 = o2.contains("org.apache.camel:");
            if (c1 && !c2) {
                return -1;
            } else if (!c1 && c2) {
                return 1;
            }
            return o1.compareTo(o2);
        });

        if (kameletsVersion == null) {
            kameletsVersion = VersionHelper.extractKameletsVersion();
        }

        // custom dependencies
        if (dependencies != null) {
            for (String d : dependencies.split(",")) {
                answer.add(d.trim());
            }
        }

        List<String> lines = Files.readAllLines(settings.toPath());
        boolean kamelets = lines.stream().anyMatch(l -> l.startsWith("kamelet="));
        for (String line : lines) {
            if (line.startsWith("dependency=")) {
                String v = StringHelper.after(line, "dependency=");
                // skip endpointdsl as its already included, and core-languages and java-joor as
                // we let quarkus compile
                boolean skip = v == null || v.contains("org.apache.camel:camel-core-languages")
                        || v.contains("org.apache.camel:camel-java-joor-dsl")
                        || v.contains("camel-endpointdsl")
                        || !kamelets && v.contains("org.apache.camel:camel-kamelet");
                if (!skip) {
                    answer.add(v);
                }
                if (kamelets && v != null && v.contains("org.apache.camel:camel-kamelet")) {
                    // include yaml-dsl and kamelet catalog if we use kamelets
                    answer.add("camel:yaml-dsl");
                    answer.add("org.apache.camel.kamelets:camel-kamelets:" + kameletsVersion);
                    answer.add("org.apache.camel.kamelets:camel-kamelets-utils:" + kameletsVersion);
                }
            } else if (line.startsWith("camel.jbang.dependencies=")) {
                String deps = StringHelper.after(line, "camel.jbang.dependencies=");
                for (String d : deps.split(",")) {
                    answer.add(d.trim());
                    if (kamelets && d.contains("org.apache.camel:camel-kamelet")) {
                        // include yaml-dsl and kamelet catalog if we use kamelets
                        answer.add("camel:yaml-dsl");
                        answer.add("org.apache.camel.kamelets:camel-kamelets:" + kameletsVersion);
                    }
                }
            } else if (line.startsWith("camel.jbang.classpathFiles")) {
                String deps = StringHelper.after(line, "camel.jbang.classpathFiles=");
                for (String d : deps.split(",")) {
                    // special to include local JARs in export lib folder
                    if (d.endsWith(".jar")) {
                        answer.add("lib:" + d.trim());
                    }
                }
            } else if (line.startsWith("camel.main.routesIncludePattern=")) {
                String routes = StringHelper.after(line, "camel.main.routesIncludePattern=");
                for (String r : routes.split(",")) {
                    String ext = FileUtil.onlyExt(r, true);
                    if (ext != null) {
                        // java is moved into src/main/java and compiled during build
                        // for the other DSLs we need to add dependencies
                        if ("groovy".equals(ext)) {
                            answer.add("mvn:org.apache.camel:camel-groovy-dsl");
                        } else if ("js".equals(ext)) {
                            answer.add("mvn:org.apache.camel:camel-js-dsl");
                        } else if ("jsh".equals(ext)) {
                            answer.add("mvn:org.apache.camel:camel-jsh-dsl");
                        } else if ("kts".equals(ext)) {
                            answer.add("mvn:org.apache.camel:camel-kotlin-dsl");
                        } else if ("xml".equals(ext)) {
                            answer.add("mvn:org.apache.camel:camel-xml-io-dsl");
                        } else if ("yaml".equals(ext)) {
                            answer.add("mvn:org.apache.camel:camel-yaml-dsl");
                            // is it a kamelet?
                            ext = FileUtil.onlyExt(r, false);
                            if ("kamelet.yaml".equals(ext)) {
                                answer.add("mvn:org.apache.camel.kamelets:camel-kamelets:" + kameletsVersion);
                            }
                        }
                    }
                }
            } else if (kamelets && line.startsWith("camel.component.kamelet.location=")) {
                // include kamelet catalog if we use kamelets
                answer.add("mvn:org.apache.camel.kamelets:camel-kamelets:" + kameletsVersion);
                answer.add("mvn:org.apache.camel.kamelets:camel-kamelets-utils:" + kameletsVersion);
            } else if (line.startsWith("modeline=")) {
                answer.add("camel:dsl-modeline");
            }
        }

        // include custom dependencies defined in profile
        if (profile != null && profile.exists()) {
            Properties prop = new CamelCaseOrderedProperties();
            RuntimeUtil.loadProperties(prop, profile);
            String deps = RuntimeUtil.getDependencies(prop);
            if (!deps.isBlank()) {
                for (String d : deps.split(",")) {
                    answer.add(d.trim());
                }
            }
        }

        // remove duplicate versions (keep first)
        Map<String, String> versions = new HashMap<>();
        Set<String> toBeRemoved = new HashSet<>();
        for (String line : answer) {
            MavenGav gav = MavenGav.parseGav(line);
            String ga = gav.getGroupId() + ":" + gav.getArtifactId();
            if (!versions.containsKey(ga)) {
                versions.put(ga, gav.getVersion());
            } else {
                toBeRemoved.add(line);
            }
        }
        answer.removeAll(toBeRemoved);

        return answer;
    }

    protected void copySourceFiles(
            File settings, File profile, File srcJavaDirRoot, File srcJavaDir, File srcResourcesDir, File srcCamelResourcesDir,
            File srcKameletsResourcesDir, String packageName)
            throws Exception {
        // read the settings file and find the files to copy
        Properties prop = new CamelCaseOrderedProperties();
        RuntimeUtil.loadProperties(prop, settings);

        for (String k : SETTINGS_PROP_SOURCE_KEYS) {
            String files = prop.getProperty(k);
            if (files != null && !files.isEmpty()) {
                for (String f : files.split(",")) {
                    String scheme = getScheme(f);
                    if (scheme != null) {
                        f = f.substring(scheme.length() + 1);
                    }
                    boolean skip = profile.getName().equals(f); // skip copying profile
                    if (skip) {
                        continue;
                    }
                    String ext = FileUtil.onlyExt(f, true);
                    boolean java = "java".equals(ext);
                    boolean camel = "camel.main.routesIncludePattern".equals(k);
                    boolean kamelet = "camel.component.kamelet.location".equals(k)
                            || "camel.jbang.localKameletDir".equals(k);
                    boolean jkube = "camel.jbang.jkubeFiles".equals(k);
                    File target = java ? srcJavaDir : camel ? srcCamelResourcesDir : srcResourcesDir;
                    File source = new File(f);
                    File out;
                    if (source.isDirectory()) {
                        out = target;
                    } else {
                        out = new File(target, source.getName());
                    }
                    if (!java) {
                        if (kamelet) {
                            out = srcKameletsResourcesDir;
                            safeCopy(source, out, true);
                        } else if (jkube) {
                            // file should be renamed and moved into src/main/jkube
                            f = f.replace(".jkube.yaml", ".yaml");
                            f = f.replace(".jkube.yml", ".yml");
                            out = new File(srcCamelResourcesDir.getParentFile().getParentFile(), "jkube/" + f);
                            out.mkdirs();
                            safeCopy(source, out, true);
                        } else {
                            safeCopy(source, out, true);
                        }
                    } else {
                        // need to append package name in java source file
                        List<String> lines = Files.readAllLines(source.toPath());
                        Optional<String> hasPackage = lines.stream().filter(l -> l.trim().startsWith("package ")).findFirst();
                        FileOutputStream fos;
                        if (hasPackage.isPresent()) {
                            String pn = determinePackageName(hasPackage.get());
                            if (pn != null) {
                                File dir = new File(srcJavaDirRoot, pn.replace('.', File.separatorChar));
                                dir.mkdirs();
                                out = new File(dir, source.getName());
                                fos = new FileOutputStream(out);
                            } else {
                                throw new IOException("Cannot determine package name from source: " + source);
                            }
                        } else {
                            fos = new FileOutputStream(out);
                            if (packageName != null && !"false".equalsIgnoreCase(packageName)) {
                                lines.add(0, "");
                                lines.add(0, "package " + packageName + ";");
                            }
                        }
                        for (String line : lines) {
                            adjustJavaSourceFileLine(line, fos);
                            fos.write(line.getBytes(StandardCharsets.UTF_8));
                            fos.write("\n".getBytes(StandardCharsets.UTF_8));
                        }
                        IOHelper.close(fos);
                    }
                }
            }
        }
    }

    protected void adjustJavaSourceFileLine(String line, FileOutputStream fos) throws Exception {
        // noop
    }

    protected String exportPackageName(String groupId, String artifactId, String packageName) {
        if ("false".equalsIgnoreCase(packageName)) {
            return null; // package names are turned off (we should use root package)
        }
        if (packageName != null) {
            return packageName; // use specific package name
        }

        // compute package name based on Maven GAV
        // for package name it must be in lower-case and alpha/numeric
        String s = groupId + "." + artifactId;
        StringBuilder sb = new StringBuilder();
        for (char ch : s.toCharArray()) {
            if (ch == '.' || Character.isAlphabetic(ch) || Character.isDigit(ch)) {
                ch = Character.toLowerCase(ch);
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    protected void copySettingsAndProfile(
            File settings, File profile, File targetDir,
            Function<Properties, Object> customize)
            throws Exception {
        Properties prop = new CamelCaseOrderedProperties();
        RuntimeUtil.loadProperties(prop, settings);

        Properties prop2 = new CamelCaseOrderedProperties();
        if (profile.exists()) {
            RuntimeUtil.loadProperties(prop2, profile);
        }
        prop2.putAll(prop);
        prepareApplicationProperties(prop2);

        for (Map.Entry<Object, Object> entry : prop.entrySet()) {
            String key = entry.getKey().toString();
            boolean skip = !key.startsWith("camel.main")
                    || "camel.main.routesCompileDirectory".equals(key)
                    || "camel.main.routesReloadEnabled".equals(key);
            if (skip) {
                prop2.remove(key);
            }
        }

        if (customize != null) {
            customize.apply(prop2);
        }

        FileOutputStream fos = new FileOutputStream(new File(targetDir, "application.properties"), false);
        try {
            for (Map.Entry<Object, Object> entry : prop2.entrySet()) {
                String k = entry.getKey().toString();
                String v = entry.getValue().toString();

                boolean skip = k.startsWith("camel.jbang.") || k.startsWith("jkube.");
                if (skip) {
                    continue;
                }

                // files are now loaded in classpath
                v = v.replaceAll("file:", "classpath:");
                if ("camel.main.routesIncludePattern".equals(k)) {
                    // camel.main.routesIncludePattern should remove all .java as we use move them to regular src/main/java
                    // camel.main.routesIncludePattern should remove all file: classpath: as we copy
                    // them to src/main/resources/camel where camel autoload from
                    v = Arrays.stream(v.split(","))
                            .filter(n -> !n.endsWith(".java") && !n.startsWith("file:") && !n.startsWith("classpath:"))
                            .collect(Collectors.joining(","));
                }
                if (!v.isBlank()) {
                    String line = applicationPropertyLine(k, v);
                    fos.write(line.getBytes(StandardCharsets.UTF_8));
                    fos.write("\n".getBytes(StandardCharsets.UTF_8));
                }
            }
        } finally {
            IOHelper.close(fos);
        }
    }

    protected void prepareApplicationProperties(Properties properties) {
        // noop
    }

    protected void copyMavenWrapper() throws Exception {
        File wrapper = new File(BUILD_DIR, ".mvn/wrapper");
        wrapper.mkdirs();
        // copy files
        InputStream is = ExportBaseCommand.class.getClassLoader().getResourceAsStream("maven-wrapper/mvnw");
        IOHelper.copyAndCloseInput(is, new FileOutputStream(new File(BUILD_DIR, "mvnw")));
        is = ExportBaseCommand.class.getClassLoader().getResourceAsStream("maven-wrapper/mvnw.cmd");
        IOHelper.copyAndCloseInput(is, new FileOutputStream(new File(BUILD_DIR, "mvnw.cmd")));
        is = ExportBaseCommand.class.getClassLoader().getResourceAsStream("maven-wrapper/maven-wrapper.jar");
        IOHelper.copyAndCloseInput(is, new FileOutputStream(new File(wrapper, "maven-wrapper.jar")));
        is = ExportBaseCommand.class.getClassLoader().getResourceAsStream("maven-wrapper/maven-wrapper.properties");
        IOHelper.copyAndCloseInput(is, new FileOutputStream(new File(wrapper, "maven-wrapper.properties")));
        // set execute file permission on mvnw/mvnw.cmd files
        File file = new File(BUILD_DIR, "mvnw");
        file.setExecutable(true);
        file = new File(BUILD_DIR, "mvnw.cmd");
        file.setExecutable(true);
    }

    protected void copyGradleWrapper() throws Exception {
        File wrapper = new File(BUILD_DIR, "gradle/wrapper");
        wrapper.mkdirs();
        // copy files
        InputStream is = ExportBaseCommand.class.getClassLoader().getResourceAsStream("gradle-wrapper/gradlew");
        IOHelper.copyAndCloseInput(is, new FileOutputStream(new File(BUILD_DIR, "gradlew")));
        is = ExportBaseCommand.class.getClassLoader().getResourceAsStream("gradle-wrapper/gradlew.bat");
        IOHelper.copyAndCloseInput(is, new FileOutputStream(new File(BUILD_DIR, "gradlew.bat")));
        is = ExportBaseCommand.class.getClassLoader().getResourceAsStream("gradle-wrapper/gradle-wrapper.jar");
        IOHelper.copyAndCloseInput(is, new FileOutputStream(new File(wrapper, "gradle-wrapper.jar")));
        is = ExportBaseCommand.class.getClassLoader().getResourceAsStream("gradle-wrapper/gradle-wrapper.properties");
        IOHelper.copyAndCloseInput(is, new FileOutputStream(new File(wrapper, "gradle-wrapper.properties")));
        // set execute file permission on gradlew/gradlew.cmd files
        File file = new File(BUILD_DIR, "gradlew");
        file.setExecutable(true);
        file = new File(BUILD_DIR, "gradlew.bat");
        file.setExecutable(true);
    }

    protected String applicationPropertyLine(String key, String value) {
        return key + "=" + value;
    }

    /**
     * Gets the maven repositories
     *
     * @param  prop         settings
     * @param  camelVersion the camel version
     * @return              repositories or null if none are in use
     */
    protected String getMavenRepos(File settings, Properties prop, String camelVersion) throws Exception {
        Set<String> answer = new LinkedHashSet<>();

        String propRepos = prop.getProperty("camel.jbang.repos");
        if (propRepos != null) {
            answer.add(propRepos);
        }

        if (camelVersion == null) {
            camelVersion = new DefaultCamelCatalog().getCatalogVersion();
        }
        // include apache snapshot repo if we use SNAPSHOT version of Camel
        if (camelVersion.endsWith("-SNAPSHOT")) {
            answer.add("https://repository.apache.org/content/groups/snapshots/");
        }

        // there may be additional extra repositories
        List<String> lines = Files.readAllLines(settings.toPath());
        for (String line : lines) {
            if (line.startsWith("repository=")) {
                String r = StringHelper.after(line, "repository=");
                answer.add(r);
            }
        }

        if (this.repos != null) {
            Collections.addAll(answer, this.repos.split(","));
        }

        return String.join(",", answer);
    }

    protected static boolean hasModeline(File settings) {
        try {
            List<String> lines = Files.readAllLines(settings.toPath());
            return lines.stream().anyMatch(l -> l.startsWith("modeline="));
        } catch (Exception e) {
            // ignore
        }
        return false;
    }

    protected static int httpServerPort(File settings) {
        try {
            List<String> lines = Files.readAllLines(settings.toPath());
            String port = lines.stream().filter(l -> l.startsWith("camel.jbang.platform-http.port="))
                    .map(s -> StringHelper.after(s, "=")).findFirst().orElse("-1");
            return Integer.parseInt(port);
        } catch (Exception e) {
            // ignore
        }
        return -1;
    }

    protected static void safeCopy(File source, File target, boolean override) throws Exception {
        if (!source.exists()) {
            return;
        }

        if (source.isDirectory()) {
            // flattern files if they are from a directory
            File[] children = source.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (child.isFile()) {
                        safeCopy(child, new File(target, child.getName()), override);
                    }
                }
            }
            return;
        }

        if (!target.exists()) {
            Files.copy(source.toPath(), target.toPath());
        } else if (override) {
            Files.copy(source.toPath(), target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }

    protected void safeCopy(InputStream source, File target) throws Exception {
        if (source == null) {
            return;
        }

        File dir = target.getParentFile();
        if (!dir.exists()) {
            dir.mkdirs();
        }

        if (!target.exists()) {
            Files.copy(source, target.toPath());
        }
    }

    private static String determinePackageName(String content) {
        final Matcher matcher = PACKAGE_PATTERN.matcher(content);
        return matcher.find() ? matcher.group(1) : null;
    }

    protected static MavenGav parseMavenGav(String dep) {
        MavenGav gav;
        if (dep.startsWith("lib:") && dep.endsWith(".jar")) {
            // lib:commons-lang3-3.12.0.jar
            String n = dep.substring(4);
            n = n.substring(0, n.length() - 4);
            // scan inside JAR in META-INF/maven and find pom.properties file
            gav = parseLocalJar(n);
            if (gav == null) {
                // okay JAR was not maven build
                gav = new MavenGav();
                String v = "1.0";
                String a = n;
                int pos = n.lastIndexOf("-");
                if (pos != -1) {
                    a = n.substring(0, pos);
                    v = n.substring(pos + 1);
                }
                gav.setGroupId("local");
                gav.setArtifactId(a);
                gav.setVersion(v);
                gav.setPackaging("lib");
            }
        } else {
            gav = MavenGav.parseGav(dep);
        }
        return gav;
    }

    private static MavenGav parseLocalJar(String dep) {
        File file = new File(dep + ".jar");
        if (!file.isFile() || !file.exists()) {
            return null;
        }

        try {
            JarFile jf = new JarFile(file);
            Optional<JarEntry> je = jf.stream().filter(e -> e.getName().startsWith("META-INF/maven/")
                    && e.getName().endsWith("/pom.properties")).findFirst();
            if (je.isPresent()) {
                JarEntry e = je.get();
                InputStream is = jf.getInputStream(e);
                Properties prop = new Properties();
                prop.load(is);
                IOHelper.close(is);
                MavenGav gav = new MavenGav();
                gav.setGroupId(prop.getProperty("groupId"));
                gav.setArtifactId(prop.getProperty("artifactId"));
                gav.setVersion(prop.getProperty("version"));
                gav.setPackaging("lib");
                return gav;
            }
        } catch (Exception e) {
            // ignore
        }

        return null;
    }

    protected void copyLocalLibDependencies(Set<String> deps) throws Exception {
        for (String d : deps) {
            if (d.startsWith("lib:")) {
                File libDir = new File(BUILD_DIR, "lib");
                libDir.mkdirs();
                String n = d.substring(4);
                File source = new File(n);
                File target = new File(libDir, n);
                safeCopy(source, target, true);
            }
        }
    }

    protected void exportAwsSecretsRefreshProp(Properties properties) {
        properties.setProperty("camel.vault.aws.accessKey", "<accessKey>");
        properties.setProperty("camel.vault.aws.secretKey", "<secretKey>");
        properties.setProperty("camel.vault.aws.region", "<region>");
        properties.setProperty("camel.vault.aws.useDefaultCredentialProvider", "<useDefaultCredentialProvider>");
        properties.setProperty("camel.vault.aws.refreshEnabled", "true");
        properties.setProperty("camel.vault.aws.refreshPeriod", "30000");
        properties.setProperty("camel.vault.aws.secrets", "<secrets>");
        if (runtime.equalsIgnoreCase("spring-boot")) {
            properties.setProperty("camel.springboot.context-reload-enabled", "true");
        } else {
            properties.setProperty("camel.main.context-reload-enabled", "true");
        }
    }

    protected void exportGcpSecretsRefreshProp(Properties properties) {
        properties.setProperty("camel.vault.gcp.serviceAccountKey", "<serviceAccountKey>");
        properties.setProperty("camel.vault.gcp.projectId", "<projectId>");
        properties.setProperty("camel.vault.gcp.useDefaultInstance", "<useDefaultInstance>");
        properties.setProperty("camel.vault.gcp.refreshEnabled", "true");
        properties.setProperty("camel.vault.aws.refreshPeriod", "30000");
        properties.setProperty("camel.vault.gcp.secrets", "<secrets>");
        properties.setProperty("camel.vault.gcp.subscriptionName", "<subscriptionName>");
        if (runtime.equalsIgnoreCase("spring-boot")) {
            properties.setProperty("camel.springboot.context-reload-enabled", "true");
        } else {
            properties.setProperty("camel.main.context-reload-enabled", "true");
        }
    }

    protected void exportAzureSecretsRefreshProp(Properties properties) {
        properties.setProperty("camel.vault.azure.tenantId", "<tenantId>");
        properties.setProperty("camel.vault.azure.clientId", "<clientId>");
        properties.setProperty("camel.vault.azure.clientSecret", "<clientSecret>");
        properties.setProperty("camel.vault.azure.vaultName", "<vaultName>");
        properties.setProperty("camel.vault.azure.refreshEnabled", "true");
        properties.setProperty("camel.vault.azure.refreshPeriod", "30000");
        properties.setProperty("camel.vault.azure.secrets", "<secrets>");
        properties.setProperty("camel.vault.azure.eventhubConnectionString", "<eventhubConnectionString>");
        properties.setProperty("camel.vault.azure.blobAccountName", "<blobAccountName>");
        properties.setProperty("camel.vault.azure.blobContainerName", "<blobContainerName>");
        properties.setProperty("camel.vault.azure.blobAccessKey", "<blobAccessKey>");
        if (runtime.equalsIgnoreCase("spring-boot")) {
            properties.setProperty("camel.springboot.context-reload-enabled", "true");
        } else {
            properties.setProperty("camel.main.context-reload-enabled", "true");
        }
    }

    protected List<String> getSecretProviders() {
        if (secretsRefreshProviders != null) {
            List<String> providers = Pattern.compile("\\,")
                    .splitAsStream(secretsRefreshProviders)
                    .collect(Collectors.toList());
            return providers;
        } else {
            return null;
        }
    }

    static class FilesConsumer extends ParameterConsumer<Export> {
        @Override
        protected void doConsumeParameters(Stack<String> args, Export cmd) {
            String arg = args.pop();
            cmd.files.add(arg);
        }
    }

}
