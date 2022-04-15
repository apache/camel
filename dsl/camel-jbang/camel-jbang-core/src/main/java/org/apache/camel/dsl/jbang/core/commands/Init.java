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

import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.Callable;

import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "init", description = "Initialize empty Camel integrations")
class Init implements Callable<Integer> {

    @CommandLine.Parameters(description = "Name of integration file", arity = "1")
    private String file;

    @Option(names = { "--integration" },
            description = "When creating a yaml file should it be created as a Camel K Integration CRD")
    private boolean integration;

    //CHECKSTYLE:OFF
    @Option(names = { "-h", "--help" }, usageHelp = true, description = "Display the help and sub-commands")
    private boolean helpRequested = false;
    //CHECKSTYLE:ON

    @Override
    public Integer call() throws Exception {
        String ext = FileUtil.onlyExt(file, false);
        if ("yaml".equals(ext) && integration) {
            ext = "integration.yaml";
        }

        String name = FileUtil.onlyName(file, false);
        InputStream is = Init.class.getClassLoader().getResourceAsStream("templates/" + ext + ".tmpl");
        if (is == null) {
            System.out.println("Error: unsupported file type: " + ext);
            return 1;
        }
        String context = IOHelper.loadText(is);
        IOHelper.close(is);

        context = context.replaceFirst("\\{\\{ \\.Name }}", name);
        IOHelper.writeText(context, new FileOutputStream(file, false));
        return 0;
    }
}
