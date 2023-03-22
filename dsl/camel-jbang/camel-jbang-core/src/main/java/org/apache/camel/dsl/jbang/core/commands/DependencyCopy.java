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
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.apache.camel.dsl.jbang.core.common.RuntimeUtil;
import org.apache.camel.util.CamelCaseOrderedProperties;
import org.apache.camel.util.FileUtil;
import picocli.CommandLine;

@CommandLine.Command(name = "copy",
                     description = "Copies all Camel dependencies required to run to a specific directory")
public class DependencyCopy extends Export {

    protected static final String EXPORT_DIR = ".camel-jbang/export";

    @CommandLine.Option(names = { "--output-directory" }, description = "Directory where dependencies should be copied",
                        defaultValue = "lib", required = true)
    protected String outputDirectory;

    public DependencyCopy(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        this.quiet = true; // be quiet and generate from fresh data to ensure the output is up-to-date
        return super.doCall();
    }

    @Override
    protected Integer export() throws Exception {
        Integer answer = doExport();
        if (answer == 0) {
            File buildDir = new File(EXPORT_DIR);
            Process p = Runtime.getRuntime()
                    .exec("mvn dependency:copy-dependencies -DincludeScope=compile -DexcludeGroupIds=org.fusesource.jansi,org.apache.logging.log4j -DoutputDirectory=../../"
                          + outputDirectory,
                            null,
                            buildDir);
            boolean done = p.waitFor(60, TimeUnit.SECONDS);
            if (!done) {
                answer = 1;
            }
            if (p.exitValue() != 0) {
                answer = p.exitValue();
            }
            // cleanup dir after complete
            FileUtil.removeDir(buildDir);
        }
        return answer;
    }

    protected Integer doExport() throws Exception {
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
            this.springBootVersion = prop.getProperty("camel.jbang.springBootVersion", this.springBootVersion);
        }

        // use temporary export dir
        exportDir = EXPORT_DIR;
        if (gav == null) {
            gav = "org.apache.camel:camel-jbang-dummy:1.0";
        }
        if (runtime == null) {
            runtime = "camel-main";
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

}
