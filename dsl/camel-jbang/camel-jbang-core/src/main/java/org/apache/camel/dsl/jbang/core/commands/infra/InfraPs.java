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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import picocli.CommandLine;

@CommandLine.Command(name = "ps", description = "Displays running services", sortOptions = false,
                     showDefaultValues = true)
public class InfraPs extends InfraBaseCommand {

    public InfraPs(CamelJBangMain main) {
        super(main);
    }

    @Override
    protected boolean showPidColumn() {
        return true;
    }

    @Override
    public Integer doCall() throws Exception {
        // retrieve running services to filter output
        Set<String> runningAliases = new HashSet<>();
        try {
            List<Path> pidFiles = Files.list(CommandLineHelper.getCamelDir())
                    .filter(p -> p.getFileName().toString().startsWith("infra-"))
                    .toList();

            for (Path pidFile : pidFiles) {
                String runningServiceName = pidFile.getFileName().toString().split("-")[1];
                runningAliases.add(runningServiceName);
            }
        } catch (IOException e) {
            // ignore
        }

        return listServices(rows -> {
            if (runningAliases.isEmpty()) {
                rows.clear();
            } else {
                rows.removeIf(row -> !runningAliases.contains(row.alias()));
            }
        });
    }
}
