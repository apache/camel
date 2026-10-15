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
package org.apache.camel.dsl.jbang.core.common;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * The output of an integration that runs in the background (camel run --background, camel restart): the file
 * <tt>~/.camel/&lt;pid&gt;-run.log</tt>, as nobody reads the output of a process in the background. The log stays while
 * the integration runs, and camel stop removes it (camel dirty removes the log of an integration that is gone).
 */
public final class BackgroundLog {

    private BackgroundLog() {
    }

    /**
     * The log of the integration with the given pid.
     */
    public static Path of(long pid) {
        return CommandLineHelper.getCamelDir().resolve(pid + "-run.log");
    }

    /**
     * Sends the output of the process to a new log file, which is named after the pid once the process has started (see
     * {@link #started(Path, long)}).
     *
     * @return the log file
     */
    public static Path redirect(ProcessBuilder pb) throws IOException {
        Path dir = CommandLineHelper.getCamelDir();
        Files.createDirectories(dir);
        Path log = Files.createTempFile(dir, "run-", ".log");
        pb.redirectErrorStream(true);
        pb.redirectOutput(log.toFile());
        return log;
    }

    /**
     * Names the log after the pid of the process that writes to it.
     *
     * @return the log, which keeps its first name when it cannot be renamed while the process writes to it (Windows)
     */
    public static Path started(Path log, long pid) {
        try {
            return Files.move(log, of(pid), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            return log;
        }
    }

    /**
     * Removes the log of an integration that has stopped.
     */
    public static void remove(long pid) {
        try {
            Files.deleteIfExists(of(pid));
        } catch (IOException e) {
            // the log is removed by camel dirty instead
        }
    }

    /**
     * The last lines of the log, or an empty text when there is no log.
     */
    public static String tail(Path log, int lines) {
        try {
            // leniently, as the output of a process may not be UTF-8
            List<String> all = new String(Files.readAllBytes(log), StandardCharsets.UTF_8).lines().toList();
            return String.join(System.lineSeparator(), all.subList(Math.max(0, all.size() - lines), all.size()));
        } catch (IOException e) {
            return "";
        }
    }
}
