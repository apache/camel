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
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.Properties;

import org.apache.camel.dsl.jbang.core.common.RuntimeType;
import org.apache.camel.dsl.jbang.core.common.RuntimeUtil;
import org.apache.camel.dsl.jbang.core.common.SourceScheme;
import org.apache.camel.tooling.maven.MavenGav;
import org.apache.camel.util.CamelCaseOrderedProperties;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import picocli.CommandLine.Command;

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
        // application.properties
        doLoadAndInitProfileProperties(new File("application.properties"));
        if (profile != null) {
            // override from profile specific configuration
            doLoadAndInitProfileProperties(new File("application-" + profile + ".properties"));
        }

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

        int answer;
        switch (runtime) {
            case springBoot -> {
                answer = export(new ExportSpringBoot(getMain()));
            }
            case quarkus -> {
                answer = export(new ExportQuarkus(getMain()));
            }
            case main -> {
                answer = export(new ExportCamelMain(getMain()));
            }
            default -> {
                printer().printErr("Unknown runtime: " + runtime);
                return 1;
            }
        }

        if (answer == 0 && !quiet) {
            printer().println("Project export successful!");
        }
        return answer;
    }

    private void doLoadAndInitProfileProperties(File file) throws Exception {
        if (file.exists()) {
            Properties props = new CamelCaseOrderedProperties();
            RuntimeUtil.loadProperties(props, file);
            // read runtime and gav from profile if not configured
            String rt = props.getProperty("camel.jbang.runtime");
            if (rt != null) {
                this.runtime = RuntimeType.fromValue(rt);
            }
            this.gav = props.getProperty("camel.jbang.gav", this.gav);
            // allow configuring versions from profile
            this.javaVersion = props.getProperty("camel.jbang.javaVersion", this.javaVersion);
            this.camelVersion = props.getProperty("camel.jbang.camelVersion", this.camelVersion);
            this.kameletsVersion = props.getProperty("camel.jbang.kameletsVersion", this.kameletsVersion);
            this.localKameletDir = props.getProperty("camel.jbang.localKameletDir", this.localKameletDir);
            this.quarkusGroupId = props.getProperty("camel.jbang.quarkusGroupId", this.quarkusGroupId);
            this.quarkusArtifactId = props.getProperty("camel.jbang.quarkusArtifactId", this.quarkusArtifactId);
            this.quarkusVersion = props.getProperty("camel.jbang.quarkusVersion", this.quarkusVersion);
            this.camelSpringBootVersion = props.getProperty("camel.jbang.camelSpringBootVersion", this.camelSpringBootVersion);
            this.springBootVersion = props.getProperty("camel.jbang.springBootVersion", this.springBootVersion);
            this.mavenWrapper
                    = "true".equals(props.getProperty("camel.jbang.mavenWrapper", this.mavenWrapper ? "true" : "false"));
            this.gradleWrapper
                    = "true".equals(props.getProperty("camel.jbang.gradleWrapper", this.gradleWrapper ? "true" : "false"));
            this.exportDir = props.getProperty("camel.jbang.exportDir", this.exportDir);
            this.buildTool = props.getProperty("camel.jbang.buildTool", this.buildTool);
            this.openapi = props.getProperty("camel.jbang.openApi", this.openapi);
            this.repositories = props.getProperty("camel.jbang.repos", this.repositories);
            this.mavenSettings = props.getProperty("camel.jbang.maven-settings", this.mavenSettings);
            this.mavenSettingsSecurity = props.getProperty("camel.jbang.maven-settings-security", this.mavenSettingsSecurity);
            this.mavenCentralEnabled = "true"
                    .equals(props.getProperty("camel.jbang.maven-central-enabled", mavenCentralEnabled ? "true" : "false"));
            this.mavenApacheSnapshotEnabled = "true".equals(props.getProperty("camel.jbang.maven-apache-snapshot-enabled",
                    mavenApacheSnapshotEnabled ? "true" : "false"));
            this.excludes = RuntimeUtil.getCommaSeparatedPropertyAsList(props, "camel.jbang.excludes", this.excludes);
        }
    }

    protected Integer export(ExportBaseCommand cmd) throws Exception {
        // copy properties from this to cmd
        cmd.files = this.files;
        cmd.repositories = this.repositories;
        cmd.dependencies = this.dependencies;
        cmd.runtime = this.runtime;
        cmd.name = this.name;
        cmd.gav = this.gav;
        cmd.mavenSettings = this.mavenSettings;
        cmd.mavenSettingsSecurity = this.mavenSettingsSecurity;
        cmd.mavenCentralEnabled = this.mavenCentralEnabled;
        cmd.mavenApacheSnapshotEnabled = this.mavenApacheSnapshotEnabled;
        cmd.exportDir = this.exportDir;
        cmd.cleanExportDir = this.cleanExportDir;
        cmd.fresh = this.fresh;
        cmd.download = this.download;
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

        if (!files.isEmpty()) {
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
                        if ("camel-spring-boot-engine-starter".equals(a1)) {
                            return 21;
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
        File docker = new File(buildDir, "src/main/docker");
        docker.mkdirs();
        String[] ids = gav.split(":");
        InputStream is = ExportCamelMain.class.getClassLoader().getResourceAsStream("templates/Dockerfile.tmpl");
        String context = IOHelper.loadText(is);
        IOHelper.close(is);

        String appJar = ids[1] + "-" + ids[2] + ".jar";
        context = context.replaceAll("\\{\\{ \\.AppJar }}", appJar);
        IOHelper.writeText(context, new FileOutputStream(new File(docker, "Dockerfile"), false));
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
        IOHelper.writeText(context, new FileOutputStream(new File(buildDir, "readme.md"), false));
    }
}
