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
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.support.PatternHelper;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.StringHelper;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "stop", description = "Stop a running Camel integration")
class StopProcess extends CamelCommand {

    @CommandLine.Parameters(description = "Name or pid of running Camel integration", arity = "1")
    private String name;

    public StopProcess(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer call() throws Exception {
        // we need to know the pids of the running camel integrations
        List<Long> pids;
        if (name.matches("\\d+")) {
            pids = List.of(Long.parseLong(name));
        } else {
            pids = findPids(name);
        }

        // stop by deleting the pid file
        for (Long pid : pids) {
            File dir = new File(System.getProperty("user.home"), ".camel");
            File pidFile = new File(dir, "" + pid);
            System.out.println("Stopping Camel integration (pid: " + pid + ")");
            FileUtil.deleteFile(pidFile);
        }

        return 0;
    }

    private static String extractName(ProcessHandle ph) {
        String cl = ph.info().commandLine().orElse("");
        String name = StringHelper.after(cl, "main.CamelJBang run");
        if (name != null) {
            name = name.trim();
        } else {
            name = "";
        }
        return name;
    }

    private static List<Long> findPids(String pattern) {
        List<Long> pids = new ArrayList<>();

        ProcessHandle.allProcesses()
                .forEach(ph -> {
                    String name = extractName(ph);
                    // ignore file extension so it is easier to match by name
                    name = FileUtil.onlyName(name);
                    if (PatternHelper.matchPattern(name, pattern)) {
                        pids.add(ph.pid());
                    }
                });
        return pids;
    }

}
