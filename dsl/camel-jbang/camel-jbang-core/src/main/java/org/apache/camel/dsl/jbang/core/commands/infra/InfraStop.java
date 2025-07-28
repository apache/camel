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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.PathUtils;
import org.apache.camel.support.PatternHelper;
import picocli.CommandLine;

@CommandLine.Command(name = "stop",
                     description = "Shuts down running infrastructure services", sortOptions = false, showDefaultValues = true)
public class InfraStop extends InfraBaseCommand {

    @CommandLine.Parameters(description = "Name or pid of running service(s)", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--kill" },
                        description = "To force killing the process (SIGKILL)")
    boolean kill;

    public InfraStop(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {

        Map<Long, Path> pids = findPids(name);

        // stop by deleting the pid file
        for (var entry : pids.entrySet()) {
            Path pidFile = entry.getValue();
            if (Files.exists(pidFile)) {
                printer().println("Shutting down infrastructure services (PID: " + entry.getKey() + ")");
                PathUtils.deleteFile(pidFile);
            }
        }
        if (kill) {
            for (Long pid : pids.keySet()) {
                ProcessHandle.of(pid).ifPresent(ph -> {
                    printer().println("Killing infrastructure service (PID: " + pid + ")");
                    ph.destroyForcibly();
                });
            }
        }

        return 0;
    }

    private Map<Long, Path> findPids(String name) throws Exception {
        Map<Long, Path> pids = new HashMap<>();

        // we need to know the pids of the running camel integrations
        if (!name.matches("\\d+")) {
            if (name.endsWith("!")) {
                // exclusive this name only
                name = name.substring(0, name.length() - 1);
            } else if (!name.endsWith("*")) {
                // lets be open and match all that starts with this pattern
                name = name + "*";
            }
        }

        final String pattern = name;

        List<Path> pidFiles = Files.list(CommandLineHelper.getCamelDir())
                .filter(p -> {
                    var n = p.getFileName().toString();
                    return n.startsWith("infra-") && n.endsWith(".json");
                })
                .toList();
        for (Path pidFile : pidFiles) {
            String fn = pidFile.getFileName().toString();
            String sn = fn.substring(fn.indexOf("-") + 1, fn.lastIndexOf('-'));
            String pid = fn.substring(fn.lastIndexOf("-") + 1, fn.lastIndexOf('.'));
            if (pid.equals(pattern) || PatternHelper.matchPattern(sn, pattern)) {
                pids.put(Long.valueOf(pid), pidFile);
            }
        }

        return pids;
    }

}
