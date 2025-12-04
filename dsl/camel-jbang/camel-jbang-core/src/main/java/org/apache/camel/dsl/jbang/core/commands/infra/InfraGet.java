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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import picocli.CommandLine;

@CommandLine.Command(
        name = "get",
        description = "Displays running service(s) information",
        sortOptions = false,
        showDefaultValues = true)
public class InfraGet extends InfraBaseCommand {

    @CommandLine.Parameters(description = "Name or pid of running service(s)", arity = "0..1")
    String name = "*";

    public InfraGet(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        Map<Long, Path> pids = findPids(name);

        int i = 0;
        for (var e : pids.entrySet()) {
            Path pidFile = e.getValue();
            String fn = pidFile.getFileName().toString();
            String sn = fn.substring(fn.indexOf("-") + 1, fn.lastIndexOf('-'));
            if (i > 0) {
                printer().println("");
            }
            printer().println("Service " + sn + " (PID: " + e.getKey() + ")");
            printer().println(Files.readString(pidFile));
            i++;
        }

        return 0;
    }
}
