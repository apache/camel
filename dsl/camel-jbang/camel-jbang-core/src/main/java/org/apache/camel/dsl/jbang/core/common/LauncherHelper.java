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
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.util.FileUtil;
import org.apache.camel.util.HomeHelper;

/**
 * Helper class for detecting and working with the camel-launcher runtime.
 */
public final class LauncherHelper {

    public static final String CAMEL_LAUNCHER_PROPERTY = "camel.launcher";
    public static final String CAMEL_LAUNCHER_JAR_PROPERTY = "camel.launcher.jar";

    private LauncherHelper() {
    }

    /**
     * Detects if running from camel-launcher (fat JAR) vs JBang.
     */
    public static boolean isRunningFromLauncher() {
        // Check system property (set by CamelLauncher)
        if ("true".equalsIgnoreCase(System.getProperty(CAMEL_LAUNCHER_PROPERTY))) {
            return true;
        }

        // Check filename only — substring match on full path could hit any app embedding camel-jbang-core
        String jarPath = getLauncherJarPath();
        if (jarPath == null) {
            return false;
        }
        String filename = Path.of(jarPath).getFileName().toString();
        return filename.startsWith("camel-launcher");
    }

    /**
     * Gets the path to the launcher JAR file.
     */
    public static String getLauncherJarPath() {
        // 1. Check system property first
        String jarPath = System.getProperty(CAMEL_LAUNCHER_JAR_PROPERTY);
        if (jarPath != null && !jarPath.isEmpty()) {
            return jarPath;
        }

        // 2. Try to detect from code source location
        try {
            URL location = LauncherHelper.class.getProtectionDomain()
                    .getCodeSource().getLocation();
            if (location != null) {
                return parseJarPath(location.toString());
            }
        } catch (Exception e) {
            System.err.println("WARN: Failed to detect launcher JAR path: " + e.getMessage());
        }
        return null;
    }

    /**
     * Parses a code-source URL string and returns the filesystem path to the outer JAR.
     * Handles three URL forms:
     * <ul>
     *   <li>{@code jar:nested:/outer.jar/!BOOT-INF/lib/inner.jar!/} — Spring Boot 3.2+/4.x loader</li>
     *   <li>{@code jar:file:/outer.jar!/BOOT-INF/classes/} — Spring Boot 2.x / shade plugin</li>
     *   <li>{@code file:/path/to/app.jar} — direct file URL</li>
     * </ul>
     * Uses {@link URI}-based path decoding to correctly handle percent-encoded characters
     * and Windows drive-letter paths (e.g. {@code /C:/...} → {@code C:\...}).
     * {@code indexOf("/!")} is used rather than {@code lastIndexOf} so that a JAR whose
     * path itself contains {@code /!} (unlikely but possible) does not lose its prefix.
     */
    static String parseJarPath(String urlStr) {
        try {
            if (urlStr.startsWith("jar:nested:")) {
                // Spring Boot 3.2+/4.x: jar:nested:/outer.jar/!BOOT-INF/lib/inner.jar!/
                String path = urlStr.substring("jar:nested:".length());
                int idx = path.indexOf("/!");
                if (idx > 0) {
                    return Path.of(URI.create("file:" + path.substring(0, idx))).toString();
                }
            } else if (urlStr.startsWith("jar:file:")) {
                // Spring Boot 2.x / shade plugin: jar:file:/outer.jar!/BOOT-INF/classes/
                int idx = urlStr.indexOf("!/");
                if (idx > 0) {
                    return Path.of(URI.create(urlStr.substring("jar:".length(), idx))).toString();
                }
            } else if (urlStr.startsWith("file:")) {
                return Path.of(URI.create(urlStr)).toString();
            }
        } catch (Exception e) {
            System.err.println("WARN: Failed to parse JAR path from URL '" + urlStr + "': " + e.getMessage());
        }
        return null;
    }

    /**
     * Gets the command to spawn a new camel process.
     */
    public static List<String> getCamelCommand() {
        List<String> cmds = new ArrayList<>();

        if (isRunningFromLauncher()) {
            String jarPath = getLauncherJarPath();
            if (jarPath != null) {
                cmds.add(getJavaCommand());
                // Forward -D and -X JVM arguments so child processes inherit proxy, truststore,
                // and memory settings. Skips -javaagent/-agentlib flags to avoid port conflicts.
                ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                        .filter(arg -> arg.startsWith("-D") || arg.startsWith("-X"))
                        .forEach(cmds::add);
                cmds.add("-jar");
                cmds.add(jarPath);
                return cmds;
            }
            // Launcher detected but JAR path unresolvable — log raw URL to aid diagnosis
            try {
                URL location = LauncherHelper.class.getProtectionDomain().getCodeSource().getLocation();
                System.err.println(
                        "WARN: Running from launcher but JAR path could not be resolved; falling back to 'camel'. Code-source URL: "
                                   + location);
            } catch (Exception ignored) {
                System.err.println(
                        "WARN: Running from launcher but JAR path could not be resolved; falling back to 'camel'.");
            }
        }

        // Fall back to JBang-style command
        if (FileUtil.isWindows()) {
            String jbangDir = System.getenv().getOrDefault("JBANG_DIR",
                    HomeHelper.resolveHomeDir() + "\\.jbang");
            cmds.add(jbangDir + "\\bin\\camel.cmd");
        } else {
            cmds.add("camel");
        }
        return cmds;
    }

    private static String getJavaCommand() {
        String javaHome = System.getProperty("java.home");
        if (javaHome != null) {
            StringBuilder javaBin = new StringBuilder(javaHome);
            javaBin.append(File.separator).append("bin").append(File.separator).append("java");
            if (FileUtil.isWindows()) {
                javaBin.append(".exe");
            }
            File javaBinFile = new File(javaBin.toString());
            if (javaBinFile.exists()) {
                return javaBin.toString();
            }
        }
        return "java";
    }
}
