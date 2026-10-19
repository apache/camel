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
import org.apache.camel.dsl.jbang.core.common.PreviousRun;
import org.apache.camel.util.FileUtil;
import picocli.CommandLine;

import static org.apache.camel.dsl.jbang.core.common.CommandLineHelper.getCamelDir;

@CommandLine.Command(name = "dirty",
                     description = "Check if there are dirty files from previous Camel runs that did not terminate gracefully",
                     footer = {
                             "%nExamples:",
                             "  camel dirty",
                             "  camel dirty --clean" })
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
        for (File f : files) {
            if (PreviousRun.isPreviousRunFile(f.getName())) {
                // kept on purpose: the log of the previous run of an integration (camel log --previous)
                continue;
            }
            // the files of a process that is alive are in use (such as the launcher of an integration that runs in a
            // child process, whose Camel has stopped), and so are the files of ourselves
            Long pid = pidOf(f.getName());
            boolean running = pid != null && ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
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

    /**
     * The pid a file in the Camel directory belongs to, such as 123 for {@code 123-status.json}, {@code 123} and the
     * infra service file {@code infra-kafka-123.json}, or <tt>null</tt> for a file that belongs to no process.
     */
    static Long pidOf(String fileName) {
        if (fileName.startsWith("infra-") && fileName.lastIndexOf('-') > 5) {
            // infra-<service>-<pid>.json and .log
            fileName = fileName.substring(fileName.lastIndexOf('-') + 1);
        }
        int end = 0;
        while (end < fileName.length() && Character.isDigit(fileName.charAt(end))) {
            end++;
        }
        boolean separated = end == fileName.length() || fileName.charAt(end) == '-' || fileName.charAt(end) == '.';
        if (end == 0 || !separated) {
            return null;
        }
        try {
            return Long.parseLong(fileName.substring(0, end));
        } catch (NumberFormatException e) {
            return null;
        }
    }

}
