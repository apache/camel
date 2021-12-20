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
import java.io.IOException;
import java.util.concurrent.Callable;

import org.apache.camel.CamelException;
import org.apache.camel.dsl.jbang.core.common.exceptions.ResourceAlreadyExists;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "binding", description = "Create a new Kamelet Binding")
class InitBinding extends AbstractInitKamelet implements Callable<Integer> {
    //CHECKSTYLE:OFF
    @Option(names = { "-h", "--help" }, usageHelp = true, description = "Display the help and sub-commands")
    private boolean helpRequested = false;
    //CHECKSTYLE:ON

    @Option(names = { "--base-resource-location" }, defaultValue = "github:apache", hidden = true,
            description = "Where to download the resources from (used for development/testing)")
    private String baseResourceLocation;

    @Option(names = { "--branch" }, defaultValue = "main", hidden = true,
            description = "The branch to use when downloading resources from (used for development/testing)")
    private String branch;

    @Option(names = { "--destination" }, defaultValue = "work",
            description = "The destination directory where to download the files")
    private String destination;

    @Option(names = { "--kamelet" }, defaultValue = "",
            description = "The kamelet to create a binding for")
    private String kamelet;

    @Option(names = { "--project" }, defaultValue = "camel-k",
            description = "The project to create a binding for (either camel-k or core)")
    private String project;

    private int downloadSample() throws IOException, CamelException {
        setBranch(branch);

        String resourcePath = String.format("camel-kamelets:templates/bindings/%s/%s-binding.yaml", project, kamelet);

        setResourceLocation(baseResourceLocation, resourcePath);

        try {
            resolveResource(new File(destination));
        } catch (ResourceAlreadyExists e) {
            System.err.println(e.getMessage());
            return 1;
        }

        return 0;
    }

    @Override
    public Integer call() throws Exception {
        return downloadSample();
    }
}
