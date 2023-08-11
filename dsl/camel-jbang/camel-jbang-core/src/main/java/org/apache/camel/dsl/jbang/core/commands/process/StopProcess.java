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
package org.apache.camel.dsl.jbang.core.commands.process;

import java.io.File;
import java.util.List;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.util.FileUtil;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "stop", description = "Shuts down running Camel integrations", sortOptions = false)
public class StopProcess extends ProcessBaseCommand {

    @CommandLine.Parameters(description = "Name or pid of running Camel integration(s)", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--kill" },
                        description = "To force killing the process (SIGKILL)")
    boolean kill;

    public StopProcess(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {

        List<Long> pids = findPids(name);

        // stop by deleting the pid file
        for (Long pid : pids) {
            File dir = new File(System.getProperty("user.home"), ".camel");
            File pidFile = new File(dir, Long.toString(pid));
            if (pidFile.exists()) {
                System.out.println("Shutting down Camel integration (PID: " + pid + ")");
                FileUtil.deleteFile(pidFile);
            }
        }
        for (Long pid : pids) {
            if (kill) {
                ProcessHandle.of(pid).ifPresent(ph -> {
                    System.out.println("Killing Camel integration (PID: " + pid + ")");
                    ph.destroyForcibly();
                });
            }
        }

        return 0;
    }

}
