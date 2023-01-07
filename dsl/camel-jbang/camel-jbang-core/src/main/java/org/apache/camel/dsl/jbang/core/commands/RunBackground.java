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

import java.util.Arrays;
import java.util.List;

import org.apache.camel.main.KameletMain;
import org.apache.camel.util.StringHelper;
import picocli.CommandLine;

@CommandLine.Command(name = "run-background", description = "Run Camel integration in background")
public class RunBackground extends Run {

    public RunBackground(CamelJBangMain main) {
        super(main);
    }

    @Override
    protected int runKameletMain(KameletMain main) throws Exception {
        // instead of running main we run camel again as background process
        String cmd = ProcessHandle.current().info().commandLine().orElse(null);
        if (cmd != null) {
            cmd = StringHelper.after(cmd, "main.CamelJBang ");
        }
        if (cmd == null) {
            System.err.println("No Camel integration files to run");
            return 1;
        }
        cmd = cmd.replaceFirst("run-background", "run");
        cmd = "camel " + cmd;

        ProcessBuilder pb = new ProcessBuilder();
        String[] arr = cmd.split("\\s+");
        List<String> args = Arrays.asList(arr);
        pb.command(args);
        Process p = pb.start();
        System.out.println("Running Camel integration: " + name + " in background with PID: " + p.pid());
        return 0;
    }

}
