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
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.util.FileUtil;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.json.JsonObject;

public final class ProcessHelper {

    private static final String[] DSL_EXT = new String[] { "java", "xml", "yaml" };
    private static final Pattern PATTERN = Pattern.compile("([\\w|\\-.])+");

    /**
     * The smallest size of a page: on Linux the JDK reads one page of <tt>/proc/&lt;pid&gt;/cmdline</tt>, and cuts a
     * longer command line at that size.
     */
    private static final int PAGE_SIZE = 4096;

    /** Where the command line of a process is read on Linux. */
    private static volatile Path procRoot = Path.of("/proc");

    private ProcessHelper() {
    }

    /**
     * Reads the command line of a process from <tt>&lt;procRoot&gt;/&lt;pid&gt;/cmdline</tt> instead of
     * <tt>/proc/&lt;pid&gt;/cmdline</tt>. Unit tests may set this to give a process the command line they need.
     *
     * @param procRoot the directory, or null for <tt>/proc</tt>
     */
    public static void useProcRoot(Path procRoot) {
        ProcessHelper.procRoot = procRoot != null ? procRoot : Path.of("/proc");
    }

    /**
     * The command line of a process: its command (the executable) and its arguments, all of them, or null when they are
     * not known.
     * <p/>
     * The JDK gives the arguments of a process ({@link ProcessHandle.Info#arguments()}) only when it has read all of
     * them. On Linux it reads one page (4 KB) of <tt>/proc/&lt;pid&gt;/cmdline</tt>: a longer command line, such as the
     * one of camel run started through jbang with its classpath of about 15 KB, has no arguments, and its
     * {@link ProcessHandle.Info#commandLine()} is cut short (CAMEL-26397). The arguments are then read from that file,
     * which has all of them. The JDK gives no arguments either of a process that has an empty one, and none at all on
     * Windows, which has no such file.
     *
     * @param  ph the process
     * @return    the command and its arguments, or null
     */
    public static List<String> commandLine(ProcessHandle ph) {
        return ph != null ? commandLine(ph, ph.info()) : null;
    }

    private static List<String> commandLine(ProcessHandle ph, ProcessHandle.Info info) {
        Optional<String> command = info.command();
        Optional<String[]> arguments = info.arguments();
        if (command.isPresent() && arguments.isPresent()) {
            List<String> answer = new ArrayList<>();
            answer.add(command.get());
            Collections.addAll(answer, arguments.get());
            return answer;
        }
        if (command.isEmpty() && info.commandLine().isEmpty()) {
            // the JDK could read nothing of its command line (such as of a process that has ended), nor can this
            return null;
        }
        List<String> answer = readCommandLine(ph.pid());
        if (answer != null && command.isPresent()) {
            // the executable the JDK gives (/proc/<pid>/exe), as it does with the arguments when it has them all
            answer.set(0, command.get());
        }
        return answer;
    }

    /**
     * The command line of a process as one text, its command and arguments separated by a space:
     * {@link ProcessHandle.Info#commandLine()}, or all of it where the JDK cuts it short (see
     * {@link #commandLine(ProcessHandle)}); empty when it is not known.
     *
     * @param  ph the process
     * @return    the command line, or empty
     */
    public static String commandLineText(ProcessHandle ph) {
        if (ph == null) {
            return "";
        }
        ProcessHandle.Info info = ph.info();
        String text = info.commandLine().orElse("");
        if (!text.isEmpty() && info.arguments().isEmpty()) {
            // a command line without its arguments: on Linux one longer than a page, cut short
            List<String> answer = commandLine(ph, info);
            if (answer != null) {
                return String.join(" ", answer);
            }
            return isCut(text) ? "" : text;
        }
        return text;
    }

    /**
     * Whether a command line that {@link ProcessHandle.Info#commandLine()} gives was cut short: on Linux the JDK cuts
     * one longer than a page at the size of the page (4 KB, or 16 or 64 KB where the pages are larger), and the end of
     * what is left is the middle of an argument, mostly of the classpath.
     *
     * @param  commandLine the command line
     * @return             true when it is as long as a page
     */
    public static boolean isCut(String commandLine) {
        int length = commandLine.length();
        return length >= PAGE_SIZE && Integer.bitCount(length) == 1;
    }

    /**
     * The command line in <tt>/proc/&lt;pid&gt;/cmdline</tt>: the command and the arguments, each one ending with a NUL
     * byte. Null when there is no such file (another OS than Linux, or a process that has ended) or it is empty.
     */
    static List<String> readCommandLine(long pid) {
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(procRoot.resolve(Long.toString(pid)).resolve("cmdline"));
        } catch (IOException | RuntimeException e) {
            return null;
        }
        // decoded as the JDK decodes the arguments of a process and encodes those of the process it starts
        Charset charset = Charset.forName(System.getProperty("native.encoding", ""), Charset.defaultCharset());
        List<String> answer = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < bytes.length; i++) {
            if (bytes[i] == 0) {
                answer.add(new String(bytes, start, i - start, charset));
                start = i + 1;
            }
        }
        if (start < bytes.length) {
            // the last one without its NUL byte, as a process that changed its arguments may leave it
            answer.add(new String(bytes, start, bytes.length - start, charset));
        }
        return answer.isEmpty() ? null : answer;
    }

    public static String extractName(JsonObject root, ProcessHandle ph) {
        String name = doExtractName(root, ph);
        return FileUtil.stripPath(name);
    }

    static String doExtractName(JsonObject root, ProcessHandle ph) {
        // favour main class if known
        if (root != null) {
            String mc = extractMainClass(root);
            if (mc != null) {
                return mc;
            }
        }
        String cl = commandLineText(ph);

        // this may be a maven plugin run that spawns a child process where Camel actually runs (link to child)
        String mvn = extractMavenPluginName(cl);
        if (mvn != null && ph != null) {
            // is camel running in any of the children?
            boolean camel = ph.children().anyMatch(ch -> !extractName(root, ch).isEmpty());
            if (camel) {
                return ""; // skip parent as we want only the child process with camel
            }
        }

        // try first camel-jbang
        String name = extractCamelJBangName(cl);
        if (name != null && !name.isEmpty()) {
            return name;
        }

        // this may be a maven plugin run that spawns a child process where Camel actually runs (link to parent)
        mvn = extractMavenPluginName(cl);
        if (mvn == null && ph != null && ph.parent().isPresent()) {
            // try parent as it may spawn a sub process
            String clp = commandLineText(ph.parent().get());
            mvn = extractMavenPluginName(clp);
        }

        name = extractCamelName(cl, mvn);
        if (name == null && root != null) {
            JsonObject jo = (JsonObject) root.get("context");
            if (jo != null) {
                name = jo.getString("name");
            }
        }

        return name == null ? "" : name;
    }

    private static String extractMavenPluginName(String cl) {
        String name = StringHelper.after(cl, "org.codehaus.plexus.classworlds.launcher.Launcher");
        if (name != null) {
            return name.trim();
        }
        return null;
    }

    static String extractMainClass(JsonObject root) {
        JsonObject runtime = (JsonObject) root.get("runtime");
        return runtime != null ? runtime.getString("mainClass") : null;
    }

    private static String extractCamelName(String cl, String mvn) {
        if (cl != null) {
            if (cl.contains("camel-spring-boot") && mvn != null) {
                int pos = cl.lastIndexOf(" ");
                if (pos != -1) {
                    String after = cl.substring(pos);
                    after = after.trim();
                    if (after.matches("[\\w|.]+")) {
                        return after;
                    }
                }
                return mvn;
            } else if (cl.contains("camel-quarkus") && mvn != null) {
                return mvn;
            } else {
                int pos = cl.lastIndexOf(" ");
                if (pos != -1) {
                    String after = cl.substring(pos);
                    after = after.trim();
                    if (after.matches("[\\w|.]+")) {
                        return after;
                    }
                }
                if (mvn != null) {
                    return mvn;
                }
            }
        }

        return null;
    }

    static String extractCamelJBangName(String cl) {
        String name = StringHelper.after(cl, "main.CamelJBang run");
        if (name != null) {
            name = name.trim();
            StringJoiner js = new StringJoiner(" ");
            // focus only on the route files supported (to skip such as readme files)
            Matcher matcher = PATTERN.matcher(name);
            while (matcher.find()) {
                String part = matcher.group();
                String ext = FileUtil.onlyExt(part, true);
                if (ext != null && Arrays.asList(DSL_EXT).contains(ext)) {
                    js.add(part);
                }
            }
            return js.toString();
        }

        return null;
    }

}
