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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.camel.dsl.jbang.core.common.RuntimeUtil;
import org.apache.camel.main.download.MavenGav;
import org.apache.camel.util.CamelCaseOrderedProperties;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StringHelper;
import picocli.CommandLine;

@CommandLine.Command(name = "export",
                     description = "Export to other runtimes such as Spring Boot or Quarkus")
abstract class ExportBaseCommand extends CamelCommand {

    protected static final String BUILD_DIR = ".camel-jbang/work";

    protected static final String[] SETTINGS_PROP_SOURCE_KEYS = new String[] {
            "camel.main.routesIncludePattern",
            "camel.component.properties.location",
            "camel.component.kamelet.location",
            "camel.jbang.classpathFiles",
            "camel.jbang.localKameletDir"
    };

    @CommandLine.Option(names = { "--profile" }, scope = CommandLine.ScopeType.INHERIT, defaultValue = "application",
                        description = "Profile to use, which refers to loading properties file with the given profile name. By default application.properties is loaded.")
    protected String profile;

    @CommandLine.Option(names = {
            "--dep", "--deps" }, description = "Add additional dependencies (Use commas to separate multiple dependencies).")
    protected String dependencies;

    @CommandLine.Option(names = { "--runtime" }, description = "Runtime (spring-boot, quarkus, or camel-main)")
    protected String runtime;

    @CommandLine.Option(names = { "--gav" }, description = "The Maven group:artifact:version")
    protected String gav;

    @CommandLine.Option(names = { "--main-classname" },
                        description = "The class name of the Camel Main application class",
                        defaultValue = "CamelApplication")
    protected String mainClassname;

    @CommandLine.Option(names = { "--java-version" }, description = "Java version (11 or 17)", defaultValue = "11")
    protected String javaVersion;

    @CommandLine.Option(names = {
            "--kamelets-version" }, description = "Apache Camel Kamelets version", defaultValue = "0.9.1")
    protected String kameletsVersion;

    @CommandLine.Option(names = { "--local-kamelet-dir" },
                        description = "Local directory for loading Kamelets (takes precedence)")
    protected String localKameletDir;

    @CommandLine.Option(names = { "--spring-boot-version" }, description = "Spring Boot version",
                        defaultValue = "2.7.5")
    protected String springBootVersion;

    @CommandLine.Option(names = { "--quarkus-group-id" }, description = "Quarkus Platform Maven groupId",
                        defaultValue = "io.quarkus.platform")
    protected String quarkusGroupId;

    @CommandLine.Option(names = { "--quarkus-artifact-id" }, description = "Quarkus Platform Maven artifactId",
                        defaultValue = "quarkus-bom")
    protected String quarkusArtifactId;

    @CommandLine.Option(names = { "--quarkus-version" }, description = "Quarkus Platform version",
                        defaultValue = "2.13.3.Final")
    protected String quarkusVersion;

    @CommandLine.Option(names = { "--maven-wrapper" }, defaultValue = "true",
                        description = "Include Maven Wrapper files in exported project")
    protected boolean mavenWrapper;

    @CommandLine.Option(names = {
            "-dir",
            "--directory" }, description = "Directory where the project will be exported", defaultValue = ".")
    protected String exportDir;

    @CommandLine.Option(names = { "--logging-level" }, defaultValue = "info", description = "Logging level")
    protected String loggingLevel;

    @CommandLine.Option(names = { "--fresh" }, description = "Make sure we use fresh (i.e. non-cached) resources")
    protected boolean fresh;

    @CommandLine.Option(names = { "--logging" }, defaultValue = "false",
                        description = "Can be used to turn on logging (logs to file in <user home>/.camel directory)")
    boolean logging;

    public ExportBaseCommand(CamelJBangMain main) {
        super(main);
    }

    public Integer call() throws Exception {
        // configure logging first
        if (logging) {
            RuntimeUtil.configureLog(loggingLevel, false, false, false, true);
        } else {
            RuntimeUtil.configureLog("off", false, false, false, true);
        }
        // export
        return export();
    }

    public String getProfile() {
        return profile;
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
        Integer code = run.runSilent();
        return code;
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

        // custom dependencies
        if (dependencies != null) {
            for (String d : dependencies.split(",")) {
                answer.add(d.trim());
            }
        }

        List<String> lines = Files.readAllLines(settings.toPath());
        for (String line : lines) {
            if (line.startsWith("dependency=")) {
                String v = StringHelper.after(line, "dependency=");
                // skip endpointdsl as its already included, and core-languages and java-joor as
                // we let quarkus compile
                boolean skip = v == null || v.contains("org.apache.camel:camel-core-languages")
                        || v.contains("org.apache.camel:camel-java-joor-dsl")
                        || v.contains("camel-endpointdsl");
                if (!skip) {
                    answer.add(v);
                }
                if (v != null && v.contains("org.apache.camel:camel-kamelet")) {
                    // include yaml-dsl and kamelet catalog if we use kamelets
                    answer.add("camel:yaml-dsl");
                    answer.add("org.apache.camel.kamelets:camel-kamelets:" + kameletsVersion);
                    answer.add("org.apache.camel.kamelets:camel-kamelets-utils:" + kameletsVersion);
                }
            } else if (line.startsWith("camel.jbang.dependencies=")) {
                String deps = StringHelper.after(line, "camel.jbang.dependencies=");
                for (String d : deps.split(",")) {
                    answer.add(d.trim());
                    if (d.contains("org.apache.camel:camel-kamelet")) {
                        // include yaml-dsl and kamelet catalog if we use kamelets
                        answer.add("camel:yaml-dsl");
                        answer.add("org.apache.camel.kamelets:camel-kamelets:" + kameletsVersion);
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
            } else if (line.startsWith("camel.component.kamelet.location=")) {
                // include kamelet catalog if we use kamelets
                answer.add("mvn:org.apache.camel.kamelets:camel-kamelets:" + kameletsVersion);
                answer.add("mvn:org.apache.camel.kamelets:camel-kamelets-utils:" + kameletsVersion);
            }
        }

        // include custom dependencies defined in profile
        if (profile != null && profile.exists()) {
            Properties prop = new CamelCaseOrderedProperties();
            RuntimeUtil.loadProperties(prop, profile);
            String deps = prop.getProperty("camel.jbang.dependencies");
            if (deps != null) {
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
            File settings, File profile, File srcJavaDir, File srcResourcesDir, File srcCamelResourcesDir,
            String packageName)
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
                    boolean camel = "camel.main.routesIncludePattern".equals(k)
                            || "camel.component.kamelet.location".equals(k)
                            || "camel.jbang.localKameletDir".equals(k);
                    File target = java ? srcJavaDir : camel ? srcCamelResourcesDir : srcResourcesDir;
                    File source = new File(f);
                    File out;
                    if (source.isDirectory()) {
                        out = target;
                    } else {
                        out = new File(target, source.getName());
                    }
                    safeCopy(source, out, true);
                    if (java) {
                        // need to append package name in java source file
                        List<String> lines = Files.readAllLines(out.toPath());
                        lines.add(0, "");
                        lines.add(0, "package " + packageName + ";");
                        FileOutputStream fos = new FileOutputStream(out);
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

        for (Map.Entry<Object, Object> entry : prop.entrySet()) {
            String key = entry.getKey().toString();
            boolean skip = "camel.main.routesCompileDirectory".equals(key)
                    || "camel.main.routesReloadEnabled".equals(key);
            if (!skip && key.startsWith("camel.main")) {
                prop2.put(entry.getKey(), entry.getValue());
            }
        }

        if (customize != null) {
            customize.apply(prop2);
        }

        FileOutputStream fos = new FileOutputStream(new File(targetDir, "application.properties"), false);
        for (Map.Entry<Object, Object> entry : prop2.entrySet()) {
            String k = entry.getKey().toString();
            String v = entry.getValue().toString();

            boolean skip = k.startsWith("camel.jbang.");
            if (skip) {
                continue;
            }

            // files are now loaded in classpath
            v = v.replaceAll("file:", "classpath:");
            if ("camel.main.routesIncludePattern".equals(k)) {
                // camel.main.routesIncludePattern should remove all .java as we use spring boot
                // to load them
                // camel.main.routesIncludePattern should remove all file: classpath: as we copy
                // them to src/main/resources/camel where camel auto-load from
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
        IOHelper.close(fos);
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

    protected String applicationPropertyLine(String key, String value) {
        return key + "=" + value;
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
}
