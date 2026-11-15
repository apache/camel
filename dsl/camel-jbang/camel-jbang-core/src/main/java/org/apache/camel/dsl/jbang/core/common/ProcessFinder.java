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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import org.apache.camel.support.PatternHelper;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/**
 * Finds the running Camel integrations on this machine, by name or pid.
 * <p/>
 * An integration is running when its process is alive and has a status file in <tt>~/.camel</tt>, which the Camel CLI
 * connector writes. An integration whose Camel has stopped (phase 9 or later, such as the launcher process of an
 * integration that runs in a child process) is not running anymore.
 * <p/>
 * A number is a pid. A name matches the name of the integration (as <tt>camel ps</tt> shows it, without file extension)
 * or its CamelContext name: it is a prefix (<tt>foo</tt> matches <tt>foo-bar</tt>) unless it ends with <tt>!</tt>
 * (<tt>foo!</tt> matches only <tt>foo</tt>), and it may use wildcards and regular expressions. When an integration has
 * exactly the given name, the name matches only the integrations of that name: with <tt>foo</tt> and <tt>foo-bar</tt>
 * running, <tt>foo</tt> matches only <tt>foo</tt>, and <tt>fo</tt> both.
 */
public final class ProcessFinder {

    /**
     * How often a command that waits for an integration to start looks at its status file, in millis. The connector
     * writes it as soon as Camel has started, since Camel 4.24, and every second.
     */
    public static final long STATUS_POLL_INTERVAL = 50;

    // the phase of a CamelContext that has stopped (see AbstractCamelContext)
    private static final int STOPPED_PHASE = 9;

    /**
     * A running Camel integration, and the status it last wrote.
     *
     * @param pid    the pid
     * @param name   the name of the integration: its main class or source file
     * @param status the status file
     */
    public record CamelProcess(long pid, String name, JsonObject status) {

        /**
         * The name of the CamelContext, or <tt>null</tt>.
         */
        public String contextName() {
            JsonObject context = context();
            return context != null ? context.getString("name") : null;
        }

        /**
         * The Camel version of the integration, or <tt>null</tt> when unknown.
         */
        public String camelVersion() {
            JsonObject context = context();
            return context != null ? context.getString("version") : null;
        }

        private JsonObject context() {
            return status != null && status.get("context") instanceof JsonObject context ? context : null;
        }
    }

    private ProcessFinder() {
    }

    /**
     * Every running Camel integration.
     */
    public static List<CamelProcess> findAll() {
        return scan(null, false);
    }

    /**
     * The running Camel integrations that match the name or pid. A name never matches this process, as the Camel CLI
     * can run Camel itself.
     *
     * @param nameOrPid a name, a pattern or a pid; <tt>null</tt> or <tt>*</tt> for every integration
     */
    public static List<CamelProcess> find(String nameOrPid) {
        if (nameOrPid == null || nameOrPid.isBlank()) {
            nameOrPid = "*";
        }
        return scan(nameOrPid, true);
    }

    /**
     * The one running Camel integration that matches the name or pid.
     *
     * @throws CliException (exit code 3) when no integration matches, or more than one
     */
    public static CamelProcess requireOne(String nameOrPid) {
        List<CamelProcess> found = find(nameOrPid);
        if (found.isEmpty()) {
            throw notFound(nameOrPid);
        }
        if (found.size() > 1) {
            throw CliException.notFound("Name or pid " + nameOrPid + " matches " + found.size()
                                        + " running Camel integrations: " + describe(found)
                                        + ". Specify the pid, or a name that matches exactly one.");
        }
        return found.get(0);
    }

    /**
     * The one running Camel integration a chaos command acts on (camel cmd chaos-add, chaos-change, chaos-remove, and
     * the chaos tools of the AI agents): the one the name or pid matches; without a name, the only one that runs, else
     * the only one of them with chaos switched on. A rule is added to one integration (its id is known only there), so
     * no name never means all of them.
     *
     * @param  nameOrPid    a name, a pattern or a pid; <tt>null</tt> or blank to pick the one integration
     * @throws CliException (exit code 3) when the name matches no integration, or more than one; (exit code 2) without
     *                      a name, when no integration runs, or several run and not exactly one of them has chaos
     *                      switched on, with the list of the running integrations to pick one from
     * @since               4.24
     */
    public static CamelProcess requireOneForChaos(String nameOrPid) {
        if (nameOrPid != null && !nameOrPid.isBlank()) {
            return requireOne(nameOrPid);
        }
        List<CamelProcess> running = find(null);
        if (running.size() == 1) {
            return running.get(0);
        }
        if (running.isEmpty()) {
            throw CliException.usage("No running Camel integrations: start one with chaos switched on (camel run --chaos)");
        }
        List<CamelProcess> on = running.stream().filter(ProcessFinder::chaosOn).toList();
        if (on.size() == 1) {
            return on.get(0);
        }
        StringJoiner sj = new StringJoiner(", ");
        for (CamelProcess p : running) {
            String chaos = chaosOn(p) ? "chaos on" : "chaos off";
            sj.add(p.name() != null && !p.name().isEmpty()
                    ? p.name() + " (pid " + p.pid() + ", " + chaos + ")" : "pid " + p.pid() + " (" + chaos + ")");
        }
        throw CliException.usage(running.size() + " Camel integrations are running and "
                                 + (on.isEmpty() ? "none has" : on.size() + " have") + " chaos switched on: " + sj
                                 + ". Give the name or pid of one.");
    }

    /**
     * Whether the integration has camel-chaos switched on, as its status says (the CLI connector puts the chaos console
     * in it).
     *
     * @since 4.24
     */
    public static boolean chaosOn(CamelProcess process) {
        return process.status() != null && process.status().get("chaos") instanceof JsonObject chaos
                && chaos.getBooleanOrDefault("enabled", false);
    }

    /**
     * The running Camel integrations that match the name or pid, at least one.
     *
     * @throws CliException (exit code 3) when no integration matches
     */
    public static List<CamelProcess> requireAny(String nameOrPid) {
        List<CamelProcess> found = find(nameOrPid);
        if (found.isEmpty()) {
            throw notFound(nameOrPid);
        }
        return found;
    }

    private static CliException notFound(String nameOrPid) {
        return CliException.notFound("No running Camel integration matches: " + nameOrPid);
    }

    private static String describe(List<CamelProcess> processes) {
        StringJoiner sj = new StringJoiner(", ");
        for (CamelProcess p : processes) {
            sj.add(p.name() != null && !p.name().isEmpty() ? p.pid() + " (" + p.name() + ")" : String.valueOf(p.pid()));
        }
        return sj.toString();
    }

    private static List<CamelProcess> scan(String nameOrPid, boolean skipThisProcess) {
        long current = ProcessHandle.current().pid();
        Map<Long, Path> statusFiles = statusFiles();
        boolean byPid = nameOrPid != null && nameOrPid.matches("\\d+");
        long pid = byPid ? Long.parseLong(nameOrPid) : -1;
        String pattern = nameOrPid == null || byPid ? null : pattern(nameOrPid);

        List<CamelProcess> answer = new ArrayList<>();
        // the live processes among those with a status file
        ProcessHandle.allProcesses()
                .filter(ph -> statusFiles.containsKey(ph.pid()))
                .forEach(ph -> {
                    if (byPid ? ph.pid() != pid : skipThisProcess && ph.pid() == current) {
                        return;
                    }
                    Path file = statusFiles.get(ph.pid());
                    if (ProcessHelper.isStale(ph, file)) {
                        // left over from an integration that is gone, and the pid is now another process
                        return;
                    }
                    JsonObject status = readStatus(file);
                    if (status == null || stopped(status)) {
                        return;
                    }
                    String name = ProcessHelper.extractName(status, ph);
                    if (pattern == null || matches(name, status, pattern)) {
                        answer.add(new CamelProcess(ph.pid(), name, status));
                    }
                });
        answer.sort(Comparator.comparingLong(CamelProcess::pid));
        if (answer.size() > 1 && isPlainName(nameOrPid, byPid)) {
            // orders matches orders-v2 too, unless an integration is named orders
            List<CamelProcess> same = answer.stream().filter(p -> hasName(p, nameOrPid)).toList();
            if (!same.isEmpty()) {
                return new ArrayList<>(same);
            }
        }
        return answer;
    }

    // a name as it is given, not a pid, an exact name (foo!) or a pattern (foo*)
    private static boolean isPlainName(String nameOrPid, boolean byPid) {
        return nameOrPid != null && !byPid && !nameOrPid.endsWith("!") && !nameOrPid.endsWith("*");
    }

    private static boolean hasName(CamelProcess p, String name) {
        if (p.name() != null && (p.name().equalsIgnoreCase(name) || name.equalsIgnoreCase(FileUtil.onlyName(p.name())))) {
            return true;
        }
        String contextName = p.contextName();
        return contextName != null && !"CamelJBang".equals(contextName) && contextName.equalsIgnoreCase(name);
    }

    private static String pattern(String name) {
        if (name.endsWith("!")) {
            // exactly this name
            return name.substring(0, name.length() - 1);
        }
        // a prefix
        return name.endsWith("*") ? name : name + "*";
    }

    private static boolean matches(String name, JsonObject status, String pattern) {
        // without file extension, so it is easier to match by name
        String onlyName = FileUtil.onlyName(name);
        if (onlyName != null && !onlyName.isEmpty() && PatternHelper.matchPattern(onlyName, pattern)) {
            return true;
        }
        if (status.get("context") instanceof JsonObject context) {
            String contextName = context.getString("name");
            // the default name of the Camel CLI, which says nothing about the integration
            if (contextName != null && !contextName.isEmpty() && !"CamelJBang".equals(contextName)) {
                return PatternHelper.matchPattern(contextName, pattern);
            }
        }
        return false;
    }

    private static boolean stopped(JsonObject status) {
        return status.get("context") instanceof JsonObject context
                && context.getIntegerOrDefault("phase", 0) >= STOPPED_PHASE;
    }

    // the status files in ~/.camel, by pid: listing them is much faster than looking at every process
    private static Map<Long, Path> statusFiles() {
        Map<Long, Path> answer = new HashMap<>();
        File[] files = CommandLineHelper.getCamelDir().toFile().listFiles((d, n) -> n.matches("\\d+-status\\.json"));
        if (files != null) {
            for (File f : files) {
                String n = f.getName();
                answer.put(Long.parseLong(n.substring(0, n.indexOf('-'))), f.toPath());
            }
        }
        return answer;
    }

    private static JsonObject readStatus(Path file) {
        try {
            String text = Files.readString(file);
            return text.isBlank() ? null : Jsoner.deserialize(text, (JsonObject) null);
        } catch (Exception e) {
            // being written, or gone
            return null;
        }
    }
}
