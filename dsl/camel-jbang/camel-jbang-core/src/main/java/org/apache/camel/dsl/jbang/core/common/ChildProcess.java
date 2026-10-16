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

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * A process the CLI starts and stops with itself, such as the Maven of camel run with a Spring Boot or Quarkus project:
 * when the CLI exits (such as on Ctrl+C), the process and the processes it started are stopped, and then the files of
 * the run are removed.
 */
public final class ChildProcess {

    /**
     * How long a process gets to stop before it is killed, in seconds.
     */
    static final int STOP_SECONDS = 30;

    /**
     * What to remove once the process has stopped.
     */
    @FunctionalInterface
    public interface Cleanup {
        void run() throws Exception;
    }

    private ChildProcess() {
    }

    /**
     * Stops the process, and the processes it started, when this JVM exits, and then runs the cleanup.
     *
     * @param process the process, or <tt>null</tt> while it has not started
     * @param cleanup what to remove once the process has stopped
     */
    public static void stopOnExit(Supplier<Process> process, Cleanup cleanup) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Process p = process.get();
            if (p != null) {
                stop(p, STOP_SECONDS);
            }
            try {
                cleanup.run();
            } catch (Exception e) {
                // best effort, as the JVM exits
            }
        }, "CamelChildProcessStop"));
    }

    /**
     * Stops the process and the processes it started (such as the app that Maven runs in a JVM of its own), and kills
     * those that have not stopped within the seconds.
     */
    public static void stop(Process process, int seconds) {
        List<ProcessHandle> children = process.descendants().toList();
        process.destroy();
        children.forEach(ProcessHandle::destroy);
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(seconds);
        try {
            if (!process.waitFor(seconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
            for (ProcessHandle child : children) {
                long remaining = deadline - System.currentTimeMillis();
                if (child.isAlive() && remaining > 0) {
                    child.onExit().get(remaining, TimeUnit.MILLISECONDS);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            // still alive: killed below
        }
        children.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
    }
}
