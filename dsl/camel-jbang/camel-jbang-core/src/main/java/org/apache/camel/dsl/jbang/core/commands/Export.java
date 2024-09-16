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
import java.util.Comparator;
import java.util.Properties;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.dsl.jbang.core.common.RuntimeType;
import org.apache.camel.dsl.jbang.core.common.RuntimeUtil;
import org.apache.camel.dsl.jbang.core.common.SourceScheme;
import org.apache.camel.tooling.maven.MavenGav;
import org.apache.camel.util.CamelCaseOrderedProperties;
import org.apache.camel.util.FileUtil;
import picocli.CommandLine.Command;

@Command(name = "export",
         description = "Export to other runtimes (Camel Main, Spring Boot, or Quarkus)")
public class Export extends ExportBaseCommand {

    public Export(CamelJBangMain main) {
        super(main);
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
            System.err.println("The runtime option must be specified");
            return 1;
        }

        if (gav == null) {
            gav = "org.example.project:%s:%s".formatted(getProjectName(), getVersion());
        }

        switch (runtime) {
            case springBoot -> {
                return export(new ExportSpringBoot(getMain()));
            }
            case quarkus -> {
                return export(new ExportQuarkus(getMain()));
            }
            case main -> {
                return export(new ExportCamelMain(getMain()));
            }
            default -> {
                System.err.println("Unknown runtime: " + runtime);
                return 1;
            }
        }
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
            this.repositories
                    = RuntimeUtil.getCommaSeparatedPropertyAsList(props, "camel.jbang.repositories", this.repositories);
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
        cmd.gav = this.gav;
        cmd.mavenSettings = this.mavenSettings;
        cmd.mavenSettingsSecurity = this.mavenSettingsSecurity;
        cmd.mavenCentralEnabled = this.mavenCentralEnabled;
        cmd.mavenApacheSnapshotEnabled = this.mavenApacheSnapshotEnabled;
        cmd.exportDir = this.exportDir;
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
        // run export
        return cmd.export();
    }

    protected String getProjectName() {
        if (gav != null) {
            String[] ids = gav.split(":");
            if (ids.length > 1) {
                return ids[1]; // artifactId
            }
        }

        if (!files.isEmpty()) {
            return FileUtil.onlyName(SourceScheme.onlyName(files.get(0)));
        }

        throw new RuntimeCamelException(
                "Failed to resolve project name - please provide --gav option or at least one source file");
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
                if ("org.springframework.boot".equals(g1)) {
                    return 30;
                } else if ("io.quarkus".equals(g1)) {
                    return 30;
                } else if ("org.apache.camel.quarkus".equals(g1)) {
                    String a1 = o1.getArtifactId();
                    // main/core/engine first
                    if ("camel-quarkus-core".equals(a1)) {
                        return 21;
                    }
                    return 20;
                } else if ("org.apache.camel.springboot".equals(g1)) {
                    String a1 = o1.getArtifactId();
                    // main/core/engine first
                    if ("camel-spring-boot-engine-starter".equals(a1)) {
                        return 21;
                    }
                    return 20;
                } else if ("org.apache.camel".equals(g1)) {
                    String a1 = o1.getArtifactId();
                    // main/core/engine first
                    if ("camel-main".equals(a1)) {
                        return 11;
                    }
                    return 10;
                } else if ("org.apache.camel.kamelets".equals(g1)) {
                    return 5;
                } else {
                    return 0;
                }
            }
        };
    }

}
