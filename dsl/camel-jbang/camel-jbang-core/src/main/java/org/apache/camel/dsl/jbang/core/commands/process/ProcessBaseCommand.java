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
import java.util.stream.Collectors;

import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.ProcessFinder;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

abstract class ProcessBaseCommand extends CamelCommand {

    protected ProcessBaseCommand(CamelJBangMain main) {
        super(main);
    }

    List<Long> findPids(String name) {
        // the running integrations that match the name or pid (see ProcessFinder)
        return ProcessFinder.find(name).stream().map(ProcessFinder.CamelProcess::pid)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * What a listing does with the status of a running integration.
     */
    @FunctionalInterface
    interface StatusConsumer {
        void accept(ProcessHandle ph, JsonObject root) throws Exception;
    }

    /**
     * Calls the consumer with the status of each running integration that matches the name or pid. A status that the
     * listing cannot read, such as one of an older Camel without a key the listing needs, does not break the listing of
     * the other integrations: that integration is skipped with a warning on stderr.
     *
     * @param  name         the name or pid; all the running integrations with the default {@code *}
     * @param  consumer     reads the status of an integration
     * @throws CliException with exit code 3 when a name or pid given on the command line matches no running integration
     */
    protected void forEachIntegration(String name, StatusConsumer consumer) {
        List<Long> pids = findIntegrationPids(name);
        ProcessHandle.allProcesses()
                .filter(ph -> pids.contains(ph.pid()))
                .forEach(ph -> {
                    JsonObject root = loadStatus(ph.pid());
                    if (root != null) {
                        try {
                            consumer.accept(ph, root);
                        } catch (Exception e) {
                            printer().printWarn("Skipped the integration with pid " + ph.pid()
                                                + ": cannot read its status (" + e.getClass().getSimpleName()
                                                + (e.getMessage() != null ? ": " + e.getMessage() : "") + ")");
                        }
                    }
                });
    }

    /**
     * The pids of the running integrations that match the name or pid, all of them with the default {@code *}. Without
     * a name and with nothing running, prints {@code No running Camel integrations} on stderr.
     *
     * @throws CliException with exit code 3 when a name or pid given on the command line matches no running integration
     */
    List<Long> findIntegrationPids(String name) {
        if (name == null || name.isBlank() || "*".equals(name)) {
            List<Long> pids = findPids("*");
            if (pids.isEmpty()) {
                printer().printDiagnostic("No running Camel integrations");
            }
            return pids;
        }
        return ProcessFinder.requireAny(name).stream().map(ProcessFinder.CamelProcess::pid)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public static long extractSince(ProcessHandle ph) {
        long since = 0;
        if (ph.info().startInstant().isPresent()) {
            since = ph.info().startInstant().get().toEpochMilli();
        }
        return since;
    }

    JsonObject loadStatus(long pid) {
        try {
            Path f = getStatusFile(Long.toString(pid));
            if (f != null && Files.exists(f)) {
                String text = Files.readString(f);
                return (JsonObject) Jsoner.deserialize(text);
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    JsonObject loadErrorFile(long pid) {
        try {
            Path f = getErrorFile(Long.toString(pid));
            if (f != null && Files.exists(f)) {
                String text = Files.readString(f);
                return (JsonObject) Jsoner.deserialize(text);
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    String sourceLocLine(String location) {
        while (StringHelper.countChar(location, ':') > 1) {
            location = location.substring(location.indexOf(':') + 1);
        }
        int pos = location.indexOf(':');
        // is the colon as scheme or line number
        String last = location.substring(pos + 1);
        boolean digits = last.matches("\\d+");
        if (!digits) {
            // it must be scheme so clip that
            location = location.substring(pos + 1);
        }
        location = FileUtil.stripPath(location);
        return location;
    }

}
