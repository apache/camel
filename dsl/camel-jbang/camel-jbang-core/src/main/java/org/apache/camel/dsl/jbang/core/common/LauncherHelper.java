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
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.util.FileUtil;

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

        // Check JAR path as fallback
        String jarPath = getLauncherJarPath();
        return jarPath != null && jarPath.contains("camel-launcher");
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
                String urlStr = location.toString();
                // Handle nested JAR (Spring Boot loader)
                if (urlStr.startsWith("jar:file:")) {
                    int idx = urlStr.indexOf("!/");
                    if (idx > 0) {
                        String path = urlStr.substring(9, idx);
                        // Decode URL-encoded characters (spaces, special chars)
                        return URLDecoder.decode(path, StandardCharsets.UTF_8);
                    }
                }
                // Handle direct file URL
                if (urlStr.startsWith("file:")) {
                    String path = urlStr.substring(5);
                    return URLDecoder.decode(path, StandardCharsets.UTF_8);
                }
            }
        } catch (Exception e) {
            System.err.println("WARN: Failed to detect launcher JAR path: " + e.getMessage());
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
                cmds.add("-jar");
                cmds.add(jarPath);
                return cmds;
            }
        }

        // Fall back to JBang-style command
        if (FileUtil.isWindows()) {
            String jbangDir = System.getenv().getOrDefault("JBANG_DIR",
                    System.getProperty("user.home") + "\\.jbang");
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
