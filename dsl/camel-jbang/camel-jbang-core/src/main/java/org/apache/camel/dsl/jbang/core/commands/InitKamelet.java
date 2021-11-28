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
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.Callable;

import org.apache.camel.CamelException;
import org.apache.camel.dsl.jbang.core.common.exceptions.ResourceAlreadyExists;
import org.apache.camel.dsl.jbang.core.templates.VelocityTemplateParser;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "kamelet", description = "Provide init templates for kamelets")
class InitKamelet extends AbstractInitKamelet implements Callable<Integer> {
    //CHECKSTYLE:OFF
    @Option(names = { "-h", "--help" }, usageHelp = true, description = "Display the help and sub-commands")
    private boolean helpRequested = false;
    //CHECKSTYLE:ON

    @CommandLine.ArgGroup(exclusive = true, multiplicity = "1")
    private ProcessOptions processOptions;

    static class ProcessOptions {
        //CHECKSTYLE:OFF
        @Option(names = { "--bootstrap" },
                description = "Bootstrap the Kamelet template generator - download the properties file for editing")
        private boolean bootstrap = false;
        //CHECKSTYLE:ON

        @Option(names = { "--properties-path" }, defaultValue = "", description = "Kamelet name")
        private String propertiesPath;
    }

    @Option(names = { "--base-resource-location" }, defaultValue = "github:apache", hidden = true,
            description = "Where to download the resources from (used for development/testing)")
    private String baseResourceLocation;

    @Option(names = { "--branch" }, defaultValue = "main", hidden = true,
            description = "The branch to use when downloading resources from (used for development/testing)")
    private String branch;

    @Option(names = { "--destination" }, defaultValue = "work",
            description = "The destination directory where to download the files")
    private String destination;

    @Override
    public Integer call() throws Exception {
        if (processOptions.bootstrap) {
            bootstrap();
        } else {
            generateTemplate();
        }

        return 0;
    }

    private int generateTemplate() throws IOException, CamelException {
        setBranch(branch);
        setResourceLocation(baseResourceLocation, "camel-kamelets:templates/init-template.kamelet.yaml.vm");

        File workDirectory = new File(destination);

        File localTemplateFile;
        try {
            localTemplateFile = resolveResource(workDirectory);
        } catch (ResourceAlreadyExists e) {
            System.err.println(e.getMessage());
            return 1;
        }
        localTemplateFile.deleteOnExit();

        VelocityTemplateParser templateParser = new VelocityTemplateParser(
                localTemplateFile.getParentFile(),
                processOptions.propertiesPath);

        File outputFile;
        try {
            outputFile = templateParser.getOutputFile(workDirectory);
        } catch (ResourceAlreadyExists e) {
            System.err.println(e.getMessage());
            return 1;
        }

        try (FileWriter fw = new FileWriter(outputFile)) {
            templateParser.parse(localTemplateFile.getName(), fw);
            System.out.println("Template file was written to " + outputFile);
        }

        return 0;
    }

    private int bootstrap() throws IOException, CamelException {
        try {
            super.bootstrap(branch, baseResourceLocation, destination);
            return 0;
        } catch (ResourceAlreadyExists e) {
            System.err.println(e.getMessage());

            return 1;
        }
    }
}
