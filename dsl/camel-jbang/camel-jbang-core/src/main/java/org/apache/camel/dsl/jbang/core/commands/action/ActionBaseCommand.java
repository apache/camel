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
package org.apache.camel.dsl.jbang.core.commands.action;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.Run;
import org.apache.camel.dsl.jbang.core.commands.TransformJavaRoutes;
import org.apache.camel.dsl.jbang.core.common.ActionClient;
import org.apache.camel.dsl.jbang.core.common.ActionClient.ActionResult;
import org.apache.camel.dsl.jbang.core.common.CamelJBangConstants;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.PathUtils;
import org.apache.camel.dsl.jbang.core.common.ProcessFinder;
import org.apache.camel.dsl.jbang.core.common.ProcessFinder.CamelProcess;
import org.apache.camel.main.KameletMain;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

abstract class ActionBaseCommand extends CamelCommand {

    /** The parts of Java sources the parser could not read, when the routes were read from Java sources. */
    private List<String> unreadSourceParts;

    protected ActionBaseCommand(CamelJBangMain main) {
        super(main);
    }

    protected static JsonObject getJsonObject(Path outputFile) {
        return getJsonObject(outputFile, 10000);
    }

    protected static JsonObject getJsonObject(Path outputFile, long timeout) {
        StopWatch watch = new StopWatch();
        while (watch.taken() < timeout) {
            File f = outputFile.toFile();
            try {
                // give time for response to be ready
                Thread.sleep(100);

                if (Files.exists(outputFile) && f.length() > 0) {
                    String text = Files.readString(outputFile);
                    return (JsonObject) Jsoner.deserialize(text);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } catch (Exception e) {
                // ignore
            }
        }
        return null;
    }

    List<Long> findPids(String name) {
        // the running integrations that match the name or pid (see ProcessFinder)
        return ProcessFinder.find(name).stream().map(ProcessFinder.CamelProcess::pid)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * How long to wait for the integration to answer: the {@code --timeout} of the command (see
     * {@link ActionTimeoutMixin}), else 10 seconds.
     */
    protected long timeout() {
        return optionValue("--timeout") instanceof Number n && n.longValue() > 0
                ? n.longValue() : ActionClient.DEFAULT_TIMEOUT;
    }

    /**
     * The running integrations that match the name or pid, all of them with the default name {@code *}.
     *
     * @throws CliException with exit code 3 when a name or pid given on the command line matches none
     */
    protected List<CamelProcess> findProcesses(String name) {
        if (name == null || name.isBlank() || "*".equals(name)) {
            List<CamelProcess> answer = ProcessFinder.find("*");
            if (answer.isEmpty()) {
                printer().printDiagnostic("No running Camel integrations");
            }
            return answer;
        }
        return ProcessFinder.requireAny(name);
    }

    /**
     * The one running integration that matches the name or pid.
     *
     * @throws CliException with exit code 3 when no running integration, or more than one, matches
     */
    protected CamelProcess findOne(String name) {
        return ProcessFinder.requireOne(name);
    }

    /**
     * Sends an action without a result, such as a route stop, to each integration, and waits until each confirms it. An
     * integration that fails the action, or does not answer in time, is reported on stderr.
     *
     * @param  processes the integrations
     * @param  action    the action
     * @param  options   sets the options of the action, or <tt>null</tt>
     * @return           the exit code: 0 when every integration took the action, else the code of the first one that
     *                   did not (see {@link ActionResult#exitCode()})
     */
    protected int sendAction(List<CamelProcess> processes, String action, Consumer<JsonObject> options) {
        int exit = CliExitCode.OK;
        for (CamelProcess process : processes) {
            ActionResult result = ActionClient.send(process, action, options, timeout());
            if (!result.isOk()) {
                printer().printErr(processes.size() > 1
                        ? process.name() + " (pid " + process.pid() + "): " + result.error() : result.error());
                if (exit == CliExitCode.OK) {
                    exit = result.exitCode();
                }
            }
        }
        return exit;
    }

    /**
     * Runs an action with a result on the integration, such as a thread dump, and returns the result.
     *
     * @param  process      the integration
     * @param  action       the action
     * @param  options      sets the options of the action, or <tt>null</tt>
     * @return              the answer of the integration
     * @throws CliException when the integration answers with an error (exit code 1, or 3 when what the action works on
     *                      does not exist), or does not answer in time (exit code 124)
     */
    protected JsonObject executeAction(CamelProcess process, String action, Consumer<JsonObject> options) {
        return ActionClient.execute(process, action, options, timeout()).orThrow();
    }

    static long extractSince(ProcessHandle ph) {
        long since = 0;
        if (ph != null && ph.info().startInstant().isPresent()) {
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

    /**
     * Whether a value matches the {@code --filter} of a command: names or patterns separated by comma, each matched as
     * a prefix (wildcards and regular expressions work too). A name that starts with {@code -} excludes what it
     * matches.
     *
     * @param  filter the filter
     * @param  values the values to match, such as the route id and the source file; a <tt>null</tt> value never matches
     * @return        whether one of the values matches a name of the filter, and none matches an excluding name
     */
    static boolean matchesFilter(String filter, String... values) {
        boolean included = false;
        boolean includes = false;
        for (String part : filter.split(",")) {
            String f = part.trim();
            if (f.isEmpty()) {
                continue;
            }
            boolean negate = f.startsWith("-");
            if (negate) {
                f = f.substring(1);
            }
            // make filtering easier
            if (!f.endsWith("*")) {
                f += "*";
            }
            boolean match = false;
            for (String value : values) {
                match |= value != null && PatternHelper.matchPattern(value, f);
            }
            if (negate && match) {
                return false;
            } else if (!negate) {
                includes = true;
                included |= match;
            }
        }
        return !includes || included;
    }

    /**
     * Quotes each element in the given array with {@link Pattern#quote} and compiles a case-insensitive pattern for it.
     * The array is modified in place (elements are replaced with their quoted form).
     *
     * @param  values the raw search terms
     * @return        compiled patterns ready for matching
     */
    static Pattern[] quoteAndCompilePatterns(String[] values) {
        Pattern[] patterns = new Pattern[values.length];
        for (int i = 0; i < values.length; i++) {
            values[i] = Pattern.quote(values[i]);
            patterns[i] = Pattern.compile("(?i)" + values[i]);
        }
        return patterns;
    }

    /**
     * Dumps the route structure of the given source files as JSON into a temporary work directory, so route-diagram /
     * route-topology can render from source files without a running integration. Java sources are read by the Java DSL
     * parser (see {@link TransformJavaRoutes#dumpStructure(List, String)}) without compiling them, unless
     * {@code compile} is set; other sources, or Java sources with {@code compile}, are booted through a transient Camel
     * run (see {@link Run#runTransform(boolean)}). The caller reads the dumped JSON from {@link SourceDump#workDir()}
     * and is responsible for deleting that directory when done.
     *
     * @param  sourceFiles        the source files to load
     * @param  workDirPrefix      prefix for the (pid-suffixed) temporary work directory name
     * @param  ignoreLoadingError whether to ignore route loading and compilation errors
     * @param  compile            whether to compile Java sources and boot them, instead of reading them with the parser
     * @return                    the dump location and the instant the dump started, or {@code null} if the dump failed
     *                            (an error is already printed to the user in that case)
     * @throws CliException       when a source file does not exist (exit code 3)
     */
    protected SourceDump dumpRoutesFromSource(
            List<String> sourceFiles, String workDirPrefix, boolean ignoreLoadingError, boolean compile)
            throws Exception {
        for (String name : sourceFiles) {
            File f = new File(name);
            if (!f.isFile() || !f.exists()) {
                throw CliException.notFound("File does not exist: " + name);
            }
        }

        Path workDir = Path.of(CommandLineHelper.CAMEL_JBANG_WORK_DIR, workDirPrefix + ProcessHandle.current().pid());
        PathUtils.deleteDirectory(workDir);
        final String target = workDir.toString();
        // anything this boot dumps must be newer than this: guards readers against a stale dump left over from a
        // previous --watch iteration whose cleanup above raced with (or lost against) a slow filesystem
        Instant bootStart = Instant.now();

        if (!compile && TransformJavaRoutes.applies(sourceFiles)) {
            return dumpJavaRoutes(sourceFiles, target) ? new SourceDump(workDir, bootStart) : null;
        }

        Run run = new Run(getMain()) {
            @Override
            protected void doAddInitialProperty(KameletMain main) {
                main.addInitialProperty("camel.main.dumpRoutes", "json");
                main.addInitialProperty("camel.main.dumpRoutesLog", "false");
                main.addInitialProperty("camel.main.dumpRoutesOutput", target);
                // turn debug off as this can otherwise include source location in dump
                main.addInitialProperty("camel.debug.enabled", "false");
                main.addInitialProperty(CamelJBangConstants.TRANSFORM, "true");
                main.addInitialProperty("camel.component.properties.ignoreMissingProperty", "true");
                if (ignoreLoadingError) {
                    // turn off bean method validator if ignore loading error
                    main.addInitialProperty("camel.language.bean.validate", "false");
                }
            }
        };
        run.files = sourceFiles;
        run.executionLimitOptions.maxSeconds = 1;
        int exit = run.runTransform(ignoreLoadingError);
        if (exit != 0) {
            return null;
        }
        return new SourceDump(workDir, bootStart);
    }

    /**
     * Dumps the route structure and topology JSON of Java sources read by the Java DSL parser, and prints what the
     * parser could not read.
     *
     * @return whether the routes were dumped (else an error is already printed)
     */
    protected boolean dumpJavaRoutes(List<String> sourceFiles, String target) throws Exception {
        TransformJavaRoutes.Result result = TransformJavaRoutes.dumpStructure(sourceFiles, target);
        if (!result.transformed()) {
            printer().printErr("No routes found: " + result.reason()
                               + " (use --compile to compile the source and load its routes)");
            return false;
        }
        unreadSourceParts = result.unread();
        if (!unreadSourceParts.isEmpty()) {
            // a warning on stderr: the JSON output stays one document, and lists them too (see addUnreadSourceParts)
            printer().printWarn("Routes read without compiling them; the parser could not work out these parts "
                                + "(use --compile to compile the source instead):");
            unreadSourceParts.forEach(u -> printer().printDiagnostic("  " + u));
        }
        return true;
    }

    /**
     * Adds the parts of Java sources the parser could not read to the JSON output, as an {@code unread} array, so a
     * tool reading the JSON can tell that routes may be missing from it.
     */
    protected void addUnreadSourceParts(JsonObject jo) {
        if (unreadSourceParts != null && !unreadSourceParts.isEmpty()) {
            jo.put("unread", new JsonArray(unreadSourceParts));
        }
    }

    /**
     * Result of {@link #dumpRoutesFromSource}: the temporary directory the route JSON was dumped into, and the instant
     * the transient boot started (used to tell a fresh dump apart from a stale one left by an earlier run).
     */
    protected record SourceDump(Path workDir, Instant bootStart) {
    }
}
