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
import java.util.List;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.util.FileUtil;
import picocli.CommandLine;

@CommandLine.Command(name = "stop",
                     description = "Stop an external service")
public class InfraStop extends InfraBaseCommand {

    @CommandLine.Parameters(description = "Service name", arity = "1")
    private List<String> serviceName;

    public InfraStop(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        String serviceToStop = serviceName.get(0);

        boolean serviceStopped = false;
        for (File pidFile : CommandLineHelper.getCamelDir().listFiles(
                (dir, name) -> name.startsWith("infra-" + serviceToStop))) {

            String name = pidFile.getName();
            String pid = name.substring(name.lastIndexOf("-") + 1, name.lastIndexOf('.'));

            printer().println("Shutting down service " + serviceToStop + " (PID: " + pid + ")");
            FileUtil.deleteFile(pidFile);

            ProcessHandle.of(Long.valueOf(pid)).ifPresent(ph -> ph.destroyForcibly());

            serviceStopped = true;
        }

        if (!serviceStopped) {
            printer().println("No Camel Infrastructure found with name " + serviceToStop + " found.");
        }

        return 0;
    }
}
