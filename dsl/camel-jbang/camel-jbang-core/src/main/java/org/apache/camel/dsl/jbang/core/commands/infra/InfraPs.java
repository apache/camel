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

import java.io.File;
import java.util.HashSet;
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
    public Integer doCall() throws Exception {
        // retrieve running services to filter output
        Set<String> runningAliases = new HashSet<>();
        for (File pidFile : CommandLineHelper.getCamelDir().listFiles(
                (dir, name) -> name.startsWith("infra-"))) {
            String runningServiceName = pidFile.getName().split("-")[1];
            runningAliases.add(runningServiceName);
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
