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
import org.apache.camel.dsl.jbang.run.common.PreviousRun;
import org.apache.camel.util.FileUtil;
import picocli.CommandLine;

import static org.apache.camel.dsl.jbang.core.common.CommandLineHelper.getCamelDir;

@CommandLine.Command(name = "dirty",
                     description = "Check if there are dirty files from previous Camel runs that did not terminate gracefully",
                     footer = {
                             "%nExamples:",
                             "  camel dirty",
                             "  camel dirty --clean",
                             "  camel dirty --clean --previous" })
public class Dirty extends ProcessBaseCommand {

    @CommandLine.Option(names = { "--clean" }, defaultValue = "false",
                        description = "Clean dirty files which are no longer in use")
    boolean clean;

    @CommandLine.Option(names = { "--previous" }, defaultValue = "false",
                        description = "Also clean the logs of the previous runs of the integrations (camel log"
                                      + " --previous), which are kept until then")
    boolean previous;

    // the log a log4j configuration of the CLI writes when the pid system property is not set yet
    static final String STRAY_LOG = "${sys:pid}.log";

    public Dirty(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        File[] files = getCamelDir().toFile().listFiles();
        if (files == null || files.length == 0) {
            return 0;
        }

        List<File> dirty = new ArrayList<>();
        List<File> previousRuns = new ArrayList<>();
        for (File f : files) {
            if (PreviousRun.isPreviousRunFile(f.getName())) {
                // kept on purpose: the log of the previous run of an integration (camel log --previous), one for
                // each name that ever ran, until --previous cleans them
                if (f.isFile()) {
                    previousRuns.add(f);
                }
                continue;
            }
            if (STRAY_LOG.equals(f.getName())) {
                // the log of a logging configuration whose ${sys:pid} was not set, which no process writes again
                dirty.add(f);
                continue;
            }
            // only the files of a process that is gone: a file named after its pid. The files of a process that is
            // alive are in use (such as the launcher of an integration that runs in a child process, whose Camel has
            // stopped), and so are the files of ourselves. A file that belongs to no process is left alone (such as the
            // log of a Spring Boot or Quarkus app, <name>.log, also while the app runs), and so is a directory.
            Long pid = f.isFile() ? pidOf(f.getName()) : null;
            if (pid != null && !ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)) {
                dirty.add(f);
            }
        }
        if (clean) {
            List<String> left = new ArrayList<>();
            if (!dirty.isEmpty()) {
                printer().println("Cleaned " + delete(dirty, left) + " orphan files");
            }
            if (previous && !previousRuns.isEmpty()) {
                int runs = runs(previousRuns);
                int cleaned = delete(previousRuns, left);
                printer().println("Cleaned " + cleaned + " files of the previous runs of " + runs + " integrations");
            }
            if (!left.isEmpty()) {
                printer().printWarn("Cannot delete: " + String.join(", ", left));
            }
        } else {
            if (!dirty.isEmpty()) {
                printer().println("There are " + dirty.size() + " orphan files");
            }
            if (!previousRuns.isEmpty()) {
                printer().println("There are logs of the previous runs of " + runs(previousRuns)
                                  + " integrations (camel dirty --clean --previous cleans them)");
            }
        }

        return 0;
    }

    /** Deletes the files, and returns how many; those that cannot be deleted are added to left. */
    private static int delete(List<File> files, List<String> left) {
        int cleaned = 0;
        for (File f : files) {
            if (FileUtil.deleteFile(f)) {
                cleaned++;
            } else {
                left.add(f.getName());
            }
        }
        return cleaned;
    }

    /** How many integrations the files of previous runs are of: a log and what it says about the run, per name. */
    private static int runs(List<File> previousRuns) {
        return (int) previousRuns.stream()
                .map(f -> f.getName().substring(0, f.getName().lastIndexOf('-')))
                .distinct().count();
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
