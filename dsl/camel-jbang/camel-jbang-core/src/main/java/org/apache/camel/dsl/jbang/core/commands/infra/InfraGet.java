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
package org.apache.camel.dsl.jbang.core.commands.infra;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import picocli.CommandLine;

@CommandLine.Command(name = "get", description = "Displays running service information", sortOptions = false,
                     showDefaultValues = true)
public class InfraGet extends InfraBaseCommand {

    @CommandLine.Parameters(description = "Service name", arity = "1")
    private List<String> serviceName;

    public InfraGet(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        String serviceToGet = serviceName.get(0);
        boolean found = false;
        try {
            List<Path> jsonFiles = Files.list(CommandLineHelper.getCamelDir())
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.startsWith("infra-" + serviceToGet + "-") && name.endsWith(".json");
                    })
                    .toList();

            if (!jsonFiles.isEmpty()) {
                printer().println(Files.readString(jsonFiles.get(0)));
                found = true;
            }
        } catch (IOException e) {
            // ignore
        }

        if (!found) {
            printer().println("No running service found with alias " + serviceToGet);
            return 0;
        }

        return 0;
    }
}
