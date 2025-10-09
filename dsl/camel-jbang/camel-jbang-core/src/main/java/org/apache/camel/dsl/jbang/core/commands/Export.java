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

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.Properties;

import org.apache.camel.dsl.jbang.core.common.CamelJBangConstants;
import org.apache.camel.dsl.jbang.core.common.PropertyResolver;
import org.apache.camel.dsl.jbang.core.common.RuntimeType;
import org.apache.camel.dsl.jbang.core.common.RuntimeUtil;
import org.apache.camel.dsl.jbang.core.common.SourceScheme;
import org.apache.camel.tooling.maven.MavenGav;
import org.apache.camel.util.CamelCaseOrderedProperties;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import picocli.CommandLine.Command;

import static org.apache.camel.dsl.jbang.core.common.CamelJBangConstants.BUILD_TOOL;
import static org.apache.camel.dsl.jbang.core.common.CamelJBangConstants.CAMEL_SPRING_BOOT_VERSION;
import static org.apache.camel.dsl.jbang.core.common.CamelJBangConstants.CAMEL_VERSION;
import static org.apache.camel.dsl.jbang.core.common.CamelJBangConstants.EXCLUDES;
import static org.apache.camel.dsl.jbang.core.common.CamelJBangConstants.EXPORT_DIR;
import static org.apache.camel.dsl.jbang.core.common.CamelJBangConstants.GAV;
import static org.apache.camel.dsl.jbang.core.common.CamelJBangConstants.GRADLE_WRAPPER;
import static org.apache.camel.dsl.jbang.core.common.CamelJBangConstants.JAVA_VERSION;
import static org.apache.camel.dsl.jbang.core.common.CamelJBangConstants.KAMELETS_VERSION;
import static org.apache.camel.dsl.jbang.core.common.CamelJBangConstants.LOCAL_KAMELET_DIR;
import static org.apache.camel.dsl.jbang.core.common.CamelJBangConstants.MAVEN_APACHE_SNAPSHOTS;
import static org.apache.camel.dsl.jbang.core.common.CamelJBangConstants.MAVEN_CENTRAL_ENABLED;
import static org.apache.camel.dsl.jbang.core.common.CamelJBangConstants.MAVEN_SETTINGS;
import static org.apache.camel.dsl.jbang.core.common.CamelJBangConstants.MAVEN_SETTINGS_SECURITY;
import static org.apache.camel.dsl.jbang.core.common.CamelJBangConstants.MAVEN_WRAPPER;
import static org.apache.camel.dsl.jbang.core.common.CamelJBangConstants.OPEN_API;
import static org.apache.camel.dsl.jbang.core.common.CamelJBangConstants.QUARKUS_ARTIFACT_ID;
import static org.apache.camel.dsl.jbang.core.common.CamelJBangConstants.QUARKUS_GROUP_ID;
import static org.apache.camel.dsl.jbang.core.common.CamelJBangConstants.QUARKUS_VERSION;
import static org.apache.camel.dsl.jbang.core.common.CamelJBangConstants.REPOS;
import static org.apache.camel.dsl.jbang.core.common.CamelJBangConstants.SPRING_BOOT_VERSION;

@Command(name = "export",
         description = "Export to other runtimes (Camel Main, Spring Boot, or Quarkus)", sortOptions = false,
         showDefaultValues = true)
public class Export extends ExportBaseCommand {

    public Export(CamelJBangMain main) {
        super(main);
    }

    @Override
    public boolean disarrangeLogging() {
        return false; // export logs specially to a camel-export.log
    }

    @Override
    protected Integer export() throws Exception {
        int answer = doExport();
        if (answer == 0 && !quiet) {
            printer().println("Project export successful!");
        }
        return answer;
    }

    protected Integer doExport() throws Exception {
        Path baseDir = exportBaseDir != null ? exportBaseDir : Path.of(".");

        // special if user type: camel run . or camel run dirName
        if (files != null && files.size() == 1) {
            String name = FileUtil.stripTrailingSeparator(files.get(0));
            Path first = Path.of(name);
            if (Files.isDirectory(first)) {
                baseDir = first;
                RunHelper.dirToFiles(name, files);
            }
        }

        // application.properties
        doLoadAndInitProfileProperties(baseDir.resolve("application.properties"));
        if (profile != null) {
            // override from profile specific configuration
            doLoadAndInitProfileProperties(baseDir.resolve("application-" + profile + ".properties"));
        }

        // property overrides from system properties for supported properties
        overrideFromSystemProperties();

        if (runtime == null) {
            printer().printErr("The runtime option must be specified");
            return 1;
        }

        if (gav == null) {
            String pn = getProjectName();
            if (pn == null) {
                printer().printErr("Failed to resolve project name: Please provide --name, --gav or source file");
                return 1;
            }
            gav = "org.example.project:%s:%s".formatted(pn, getVersion());
        }

        try {
            verifyExportFiles();
        } catch (FileNotFoundException ex) {
            printer().println(ex.getMessage());
            return 1;
        }

        switch (runtime) {
            case springBoot -> {
                return export(baseDir, new ExportSpringBoot(getMain()));
            }
            case quarkus -> {
                return export(baseDir, new ExportQuarkus(getMain()));
            }
            case main -> {
                return export(baseDir, new ExportCamelMain(getMain()));
            }
            default -> {
                printer().printErr("Unknown runtime: " + runtime);
                return 1;
            }
        }
    }

    private void verifyExportFiles() throws FileNotFoundException {
        for (var fn : files) {
            if (fn.indexOf(':') < 0 || fn.startsWith("file:")) {
                if (fn.startsWith("file:")) {
                    fn = fn.substring(5);
                    if (fn.startsWith("//")) {
                        fn = fn.substring(2);
                    }
                }
                if (fn.endsWith("/*")) {
                    fn = fn.substring(0, fn.length() - 2);
                }
                if (!Files.exists(Paths.get(fn))) {
                    throw new FileNotFoundException("Path does not exist: " + fn);
                }
            }
        }
    }

    private void doLoadAndInitProfileProperties(Path path) throws Exception {
        if (Files.exists(path)) {
            Properties props = new CamelCaseOrderedProperties();
            RuntimeUtil.loadProperties(props, path);
            // read runtime and gav from profile if not configured
            String rt = props.getProperty(CamelJBangConstants.RUNTIME);
            if (rt != null) {
                this.runtime = RuntimeType.fromValue(rt);
            }
            this.gav = props.getProperty(GAV, this.gav);
            // allow configuring versions from profile
            this.javaVersion = props.getProperty(JAVA_VERSION, this.javaVersion);
            this.camelVersion = props.getProperty(CAMEL_VERSION, this.camelVersion);
            this.kameletsVersion = props.getProperty(KAMELETS_VERSION, this.kameletsVersion);
            this.localKameletDir = props.getProperty(LOCAL_KAMELET_DIR, this.localKameletDir);
            this.quarkusGroupId = props.getProperty(QUARKUS_GROUP_ID, this.quarkusGroupId);
            this.quarkusArtifactId = props.getProperty(QUARKUS_ARTIFACT_ID, this.quarkusArtifactId);
            this.quarkusVersion = props.getProperty(QUARKUS_VERSION, this.quarkusVersion);
            this.camelSpringBootVersion = props.getProperty(CAMEL_SPRING_BOOT_VERSION, this.camelSpringBootVersion);
            this.springBootVersion = props.getProperty(SPRING_BOOT_VERSION, this.springBootVersion);
            this.mavenWrapper
                    = "true".equals(props.getProperty(MAVEN_WRAPPER, this.mavenWrapper ? "true" : "false"));
            this.gradleWrapper
                    = "true".equals(props.getProperty(GRADLE_WRAPPER, this.gradleWrapper ? "true" : "false"));
            this.exportDir = props.getProperty(EXPORT_DIR, this.exportDir);
            this.buildTool = props.getProperty(BUILD_TOOL, this.buildTool);
            this.openapi = props.getProperty(OPEN_API, this.openapi);
            this.repositories = props.getProperty(REPOS, this.repositories);
            this.mavenSettings = props.getProperty(MAVEN_SETTINGS, this.mavenSettings);
            this.mavenSettingsSecurity = props.getProperty(MAVEN_SETTINGS_SECURITY, this.mavenSettingsSecurity);
            this.mavenCentralEnabled = "true"
                    .equals(props.getProperty(MAVEN_CENTRAL_ENABLED, mavenCentralEnabled ? "true" : "false"));
            this.mavenApacheSnapshotEnabled = "true".equals(props.getProperty(MAVEN_APACHE_SNAPSHOTS,
                    mavenApacheSnapshotEnabled ? "true" : "false"));
            this.excludes = RuntimeUtil.getCommaSeparatedPropertyAsList(props, EXCLUDES, this.excludes);
        }
    }

    /**
     * For supported fields prefer the values from system properties if they are defined.
     */
    private void overrideFromSystemProperties() {
        this.quarkusGroupId = PropertyResolver.fromSystemProperty(QUARKUS_GROUP_ID, () -> this.quarkusGroupId);
        this.quarkusArtifactId = PropertyResolver.fromSystemProperty(QUARKUS_ARTIFACT_ID, () -> this.quarkusArtifactId);
        this.quarkusVersion = PropertyResolver.fromSystemProperty(QUARKUS_VERSION, () -> this.quarkusVersion);
        this.camelSpringBootVersion
                = PropertyResolver.fromSystemProperty(CAMEL_SPRING_BOOT_VERSION, () -> this.camelSpringBootVersion);
    }

    protected Integer export(Path exportBaseDir, ExportBaseCommand cmd) throws Exception {
        // copy properties from this to cmd
        cmd.exportBaseDir = exportBaseDir;
        cmd.files = this.files;
        cmd.repositories = this.repositories;
        cmd.dependencies = this.dependencies;
        cmd.runtime = this.runtime;
        cmd.name = this.name;
        cmd.port = this.port;
        cmd.managementPort = this.managementPort;
        cmd.observe = this.observe;
        cmd.gav = this.gav;
        cmd.mavenSettings = this.mavenSettings;
        cmd.mavenSettingsSecurity = this.mavenSettingsSecurity;
        cmd.mavenCentralEnabled = this.mavenCentralEnabled;
        cmd.mavenApacheSnapshotEnabled = this.mavenApacheSnapshotEnabled;
        cmd.exportDir = this.exportDir;
        cmd.cleanExportDir = this.cleanExportDir;
        cmd.fresh = this.fresh;
        cmd.download = this.download;
        cmd.skipPlugins = this.skipPlugins;
        cmd.packageScanJars = this.packageScanJars;
        cmd.javaVersion = this.javaVersion;
        cmd.camelVersion = this.camelVersion;
        cmd.kameletsVersion = this.kameletsVersion;
        cmd.profile = this.profile;
        cmd.localKameletDir = this.localKameletDir;
        cmd.logging = this.logging;
        cmd.loggingLevel = this.loggingLevel;
        cmd.mainClassname = this.mainClassname;
        cmd.camelSpringBootVersion = this.camelSpringBootVersion;
        cmd.quarkusGroupId = this.quarkusGroupId;
        cmd.quarkusArtifactId = this.quarkusArtifactId;
        cmd.quarkusVersion = this.quarkusVersion;
        cmd.springBootVersion = this.springBootVersion;
        cmd.mavenWrapper = this.mavenWrapper;
        cmd.gradleWrapper = this.gradleWrapper;
        cmd.buildTool = this.buildTool;
        cmd.quiet = this.quiet;
        cmd.buildProperties = this.buildProperties;
        cmd.openapi = this.openapi;
        cmd.packageName = this.packageName;
        cmd.excludes = this.excludes;
        cmd.ignoreLoadingError = this.ignoreLoadingError;
        cmd.lazyBean = this.lazyBean;
        cmd.verbose = this.verbose;
        cmd.applicationProperties = this.applicationProperties;
        cmd.groovyPrecompiled = this.groovyPrecompiled;
        // run export
        return cmd.export();
    }

    protected String getProjectName() {
        if (name != null) {
            return name;
        }

        if (gav != null) {
            String[] ids = gav.split(":");
            if (ids.length > 1) {
                return ids[1]; // artifactId
            }
        }

        if (files != null && !files.isEmpty()) {
            return FileUtil.onlyName(SourceScheme.onlyName(files.get(0)));
        }

        return null;
    }

    protected String getVersion() {
        if (gav != null) {
            String[] ids = gav.split(":");
            if (ids.length > 2) {
                return ids[2]; // g:a:v version
            }
        }

        return "1.0-SNAPSHOT";
    }

    public Comparator<MavenGav> mavenGavComparator() {
        return new Comparator<MavenGav>() {
            @Override
            public int compare(MavenGav o1, MavenGav o2) {
                int r1 = rankGroupId(o1);
                int r2 = rankGroupId(o2);

                if (r1 > r2) {
                    return -1;
                } else if (r2 > r1) {
                    return 1;
                } else {
                    return o1.toString().compareTo(o2.toString());
                }
            }

            int rankGroupId(MavenGav o1) {
                String g1 = o1.getGroupId();
                if (g1 == null) {
                    return 0;
                }

                switch (g1) {
                    case "org.springframework.boot" -> {
                        return 30;
                    }
                    case "io.quarkus" -> {
                        return 30;
                    }
                    case "org.apache.camel.quarkus" -> {
                        String a1 = o1.getArtifactId();
                        // main/core/engine first
                        if ("camel-quarkus-core".equals(a1)) {
                            return 21;
                        }
                        return 20;
                    }
                    case "org.apache.camel.springboot" -> {
                        String a1 = o1.getArtifactId();
                        // main/core/engine first
                        if ("camel-spring-boot-starter".equals(a1)) {
                            return 21;
                        } else if ("camel-spring-boot-engine-starter".equals(a1)) {
                            return 22;
                        }
                        return 20;
                    }
                    case "org.apache.camel" -> {
                        String a1 = o1.getArtifactId();
                        // main/core/engine first
                        if ("camel-main".equals(a1)) {
                            return 11;
                        }
                        return 10;
                    }
                    case "org.apache.camel.kamelets" -> {
                        return 5;
                    }
                    default -> {
                        return 0;
                    }
                }
            }
        };
    }

    // Maven reproducible builds: https://maven.apache.org/guides/mini/guide-reproducible-builds.html
    protected String getBuildMavenProjectDate() {
        // 2024-09-23T10:00:00Z
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        return sdf.format(new Date());
    }

    // Copy the dockerfile into the same Maven project root directory.
    protected void copyDockerFiles(String buildDir) throws Exception {
        Path docker = Path.of(buildDir).resolve("src/main/docker");
        Files.createDirectories(docker);
        String[] ids = gav.split(":");
        // we only support and have docker files for java 17 or 21
        String v = javaVersion.equals("17") ? "17" : "21";
        InputStream is
                = ExportCamelMain.class.getClassLoader().getResourceAsStream("templates/Dockerfile" + v + ".tmpl");
        String context = IOHelper.loadText(is);
        IOHelper.close(is);

        String appJar = ids[1] + "-" + ids[2] + ".jar";
        context = context.replaceAll("\\{\\{ \\.ArtifactId }}", ids[1]);
        context = context.replaceAll("\\{\\{ \\.Version }}", ids[2]);
        context = context.replaceAll("\\{\\{ \\.AppJar }}", appJar);
        Files.writeString(docker.resolve("Dockerfile"), context);
    }

    // Copy the readme.md into the same Maven project root directory.
    protected void copyReadme(String buildDir, String appJar) throws Exception {
        String[] ids = gav.split(":");
        InputStream is = ExportCamelMain.class.getClassLoader().getResourceAsStream("templates/readme.md.tmpl");
        String context = IOHelper.loadText(is);
        IOHelper.close(is);

        context = context.replaceAll("\\{\\{ \\.ArtifactId }}", ids[1]);
        context = context.replaceAll("\\{\\{ \\.Version }}", ids[2]);
        context = context.replaceAll("\\{\\{ \\.AppRuntimeJar }}", appJar);
        Files.writeString(Path.of(buildDir).resolve("readme.md"), context);
    }
}
