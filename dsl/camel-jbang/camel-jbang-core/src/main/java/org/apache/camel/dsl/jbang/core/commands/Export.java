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

import java.nio.file.Path;

import org.apache.camel.dsl.jbang.export.ExportRequest;
import org.apache.camel.dsl.jbang.export.ProjectExporter;
import org.apache.camel.dsl.jbang.export.common.PropertyResolver;
import org.apache.camel.dsl.jbang.export.common.QuarkusPlatform;
import picocli.CommandLine.Command;

import static org.apache.camel.dsl.jbang.export.common.CamelJBangConstants.CAMEL_SPRING_BOOT_VERSION;

@Command(name = "export",
         description = "Export to other runtimes (Camel Main, Spring Boot, or Quarkus)", sortOptions = false,
         showDefaultValues = true,
         footer = {
                 "%nExamples:",
                 "  camel export hello.java",
                 "  camel export --runtime=spring-boot hello.java",
                 "  camel export --runtime=quarkus *",
                 "  camel export --gav=com.example:myapp:1.0 hello.java",
                 "  camel export --dry-run hello.java" })
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
        ExportRequest request = toRequest();
        ProjectExporter exporter = new ProjectExporter(exportContext());
        Path baseDir = exporter.prepare(request);
        overrideFromSystemProperties(request);
        // the options from application.properties, the profile and the system properties
        fromRequest(request);

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
            request.gav = gav;
        }

        customizeRequest(request);
        return exporter.export(baseDir, request);
    }

    /**
     * To customize the request before it is exported, such as the pom template to use.
     */
    protected void customizeRequest(ExportRequest request) {
    }

    protected String getProjectName() {
        return ProjectExporter.projectName(name, gav, files);
    }

    protected String getVersion() {
        return ProjectExporter.projectVersion(gav);
    }

    /**
     * For supported fields prefer the values from system properties if they are defined (the export engine reads no
     * system properties).
     */
    static void overrideFromSystemProperties(ExportRequest request) {
        request.quarkusPlatform = QuarkusPlatform.of(System.getProperties(), request.quarkusPlatform);
        request.camelSpringBootVersion
                = PropertyResolver.fromSystemProperty(CAMEL_SPRING_BOOT_VERSION, () -> request.camelSpringBootVersion);
    }
}
