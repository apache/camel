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

import org.apache.camel.dsl.jbang.core.common.RuntimeUtil;
import org.apache.camel.tooling.maven.MavenGav;
import org.apache.camel.util.CamelCaseOrderedProperties;
import picocli.CommandLine.Command;

@Command(name = "export",
         description = "Export to other runtimes (Camel Main, Spring Boot, or Quarkus)")
public class Export extends ExportBaseCommand {

    public Export(CamelJBangMain main) {
        super(main);
    }

    @Override
    protected Integer export() throws Exception {
        // read runtime and gav from profile if not configured
        File profile = new File(getProfile() + ".properties");
        if (profile.exists()) {
            Properties prop = new CamelCaseOrderedProperties();
            RuntimeUtil.loadProperties(prop, profile);
            if (this.runtime == null) {
                this.runtime = prop.getProperty("camel.jbang.runtime");
            }
            if (this.gav == null) {
                this.gav = prop.getProperty("camel.jbang.gav");
            }
            // allow configuring versions from profile
            this.javaVersion = prop.getProperty("camel.jbang.javaVersion", this.javaVersion);
            this.camelVersion = prop.getProperty("camel.jbang.camelVersion", this.camelVersion);
            this.kameletsVersion = prop.getProperty("camel.jbang.kameletsVersion", this.kameletsVersion);
            this.localKameletDir = prop.getProperty("camel.jbang.localKameletDir", this.localKameletDir);
            this.quarkusGroupId = prop.getProperty("camel.jbang.quarkusGroupId", this.quarkusGroupId);
            this.quarkusArtifactId = prop.getProperty("camel.jbang.quarkusArtifactId", this.quarkusArtifactId);
            this.quarkusVersion = prop.getProperty("camel.jbang.quarkusVersion", this.quarkusVersion);
            this.camelSpringBootVersion = prop.getProperty("camel.jbang.camelSpringBootVersion", this.camelSpringBootVersion);
            this.springBootVersion = prop.getProperty("camel.jbang.springBootVersion", this.springBootVersion);
            this.mavenWrapper
                    = "true".equals(prop.getProperty("camel.jbang.mavenWrapper", this.mavenWrapper ? "true" : "false"));
            this.gradleWrapper
                    = "true".equals(prop.getProperty("camel.jbang.gradleWrapper", this.gradleWrapper ? "true" : "false"));
            this.exportDir = prop.getProperty("camel.jbang.exportDir", this.exportDir);
            this.buildTool = prop.getProperty("camel.jbang.buildTool", this.buildTool);
            this.secretsRefresh
                    = "true".equals(prop.getProperty("camel.jbang.secretsRefresh", this.secretsRefresh ? "true" : "false"));
            this.secretsRefreshProviders
                    = prop.getProperty("camel.jbang.secretsRefreshProviders", this.secretsRefreshProviders);
            this.openapi = prop.getProperty("camel.jbang.openApi", this.openapi);
        }

        if (runtime == null) {
            System.err.println("The runtime option must be specified");
            return 1;
        }
        if (gav == null) {
            System.err.println("The gav option must be specified");
            return 1;
        }

        if ("spring-boot".equals(runtime) || "camel-spring-boot".equals(runtime)) {
            return export(new ExportSpringBoot(getMain()));
        } else if ("quarkus".equals(runtime) || "camel-quarkus".equals(runtime)) {
            return export(new ExportQuarkus(getMain()));
        } else if ("main".equals(runtime) || "camel-main".equals(runtime)) {
            return export(new ExportCamelMain(getMain()));
        } else {
            System.err.println("Unknown runtime: " + runtime);
            return 1;
        }
    }

    protected Integer export(ExportBaseCommand cmd) throws Exception {
        // copy properties from this to cmd
        cmd.files = this.files;
        cmd.profile = this.profile;
        cmd.repos = this.repos;
        cmd.dependencies = this.dependencies;
        cmd.runtime = this.runtime;
        cmd.gav = this.gav;
        cmd.exportDir = this.exportDir;
        cmd.fresh = this.fresh;
        cmd.javaVersion = this.javaVersion;
        cmd.camelVersion = this.camelVersion;
        cmd.kameletsVersion = this.kameletsVersion;
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
        cmd.additionalProperties = this.additionalProperties;
        cmd.secretsRefresh = this.secretsRefresh;
        cmd.secretsRefreshProviders = this.secretsRefreshProviders;
        cmd.openapi = this.openapi;
        cmd.packageName = this.packageName;
        // run export
        return cmd.export();
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
