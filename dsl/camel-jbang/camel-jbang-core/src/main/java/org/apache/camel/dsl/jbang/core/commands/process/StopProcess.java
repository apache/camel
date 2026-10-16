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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.PathUtils;
import org.apache.camel.dsl.jbang.core.common.TimeoutConverter;
import org.apache.camel.util.TimeUtils;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "stop", description = "Shuts down running Camel integrations", sortOptions = false, showDefaultValues = true,
         footer = {
                 "%nExamples:",
                 "  camel stop hello",
                 "  camel stop *",
                 "  camel stop 12345",
                 "  camel stop hello --timeout=2m",
                 "  camel stop hello --kill" })
public class StopProcess extends ProcessBaseCommand {

    @CommandLine.Parameters(description = "Name or pid of running Camel integration(s)", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--kill" },
                        description = "To force killing the process (SIGKILL)")
    boolean kill;

    @CommandLine.Option(names = { "--timeout" }, defaultValue = "60000", converter = TimeoutConverter.class,
                        description = "How long to wait for the integration to stop, in millis or as a duration such as 30s")
    long timeout = 60000;

    @CommandLine.Option(names = { "--no-wait" },
                        description = "Do not wait for the integration to stop")
    boolean noWait;

    public StopProcess(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        List<Long> pids = findIntegrationPids(name);

        for (Long pid : pids) {
            Path pidFile = CommandLineHelper.getCamelDir().resolve(Long.toString(pid));
            if (Files.exists(pidFile)) {
                // the integration stops when its pid file is gone
                printer().println("Shutting down Camel integration (PID: " + pid + ")");
                PathUtils.deleteFile(pidFile);
            } else if (!kill) {
                printer().println("Shutting down Camel integration (PID: " + pid + ") with SIGTERM");
                ProcessHandle.of(pid).ifPresent(ProcessHandle::destroy);
            }
        }
        if (kill) {
            for (Long pid : pids) {
                ProcessHandle.of(pid).ifPresent(ph -> {
                    printer().println("Killing Camel integration (PID: " + pid + ")");
                    ph.destroyForcibly();
                });
            }
        }
        if (noWait) {
            return 0;
        }

        long deadline = System.currentTimeMillis() + timeout;
        List<Long> running = new ArrayList<>();
        for (Long pid : pids) {
            if (!awaitExit(pid, deadline)) {
                running.add(pid);
            }
        }
        if (!running.isEmpty()) {
            throw CliException.timeout("Camel integration (PID: "
                                       + running.stream().map(String::valueOf).collect(Collectors.joining(", "))
                                       + ") did not stop within " + TimeUtils.printDuration(timeout)
                                       + "; use --kill");
        }
        return 0;
    }

    /**
     * Waits until the process has exited.
     *
     * @param  pid      the process
     * @param  deadline until when to wait, in epoch millis
     * @return          whether the process is gone
     */
    static boolean awaitExit(long pid, long deadline) {
        Optional<ProcessHandle> ph = ProcessHandle.of(pid);
        if (ph.isEmpty()) {
            return true;
        }
        long remaining = deadline - System.currentTimeMillis();
        try {
            if (remaining > 0) {
                ph.get().onExit().get(remaining, TimeUnit.MILLISECONDS);
            }
            return !ph.get().isAlive();
        } catch (TimeoutException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException e) {
            return !ph.get().isAlive();
        }
    }

}
