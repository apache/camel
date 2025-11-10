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
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.util.FileUtil;
import picocli.CommandLine;

import static org.apache.camel.dsl.jbang.core.common.CommandLineHelper.getCamelDir;

@CommandLine.Command(name = "dirty",
                     description = "Check if there are dirty files from previous Camel runs that did not terminate gracefully")
public class Dirty extends ProcessBaseCommand {

    @CommandLine.Option(names = { "--clean" }, defaultValue = "false",
                        description = "Clean dirty files which are no longer in use")
    boolean clean;

    public Dirty(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        File[] files = getCamelDir().toFile().listFiles(f -> !"camel-export.log".equals(f.getName()));
        if (files == null || files.length == 0) {
            return 0;
        }

        List<File> dirty = new ArrayList<>();
        List<Long> pids = findPids("*");
        pids.add(ProcessHandle.current().pid()); // include ourselves
        for (File f : files) {
            // skip running Camel integrations and ourselves
            String n = f.getName();
            boolean running = pids.stream().anyMatch(p -> n.startsWith("" + p));
            if (!running) {
                dirty.add(f);
            }
        }
        if (dirty.isEmpty()) {
            return 0;
        }

        if (clean) {
            for (File f : dirty) {
                FileUtil.deleteFile(f);
            }
            printer().println("Cleaned " + dirty.size() + " orphan files");
        } else {
            printer().println("There are " + dirty.size() + " orphan files");
        }

        return 0;
    }

}
