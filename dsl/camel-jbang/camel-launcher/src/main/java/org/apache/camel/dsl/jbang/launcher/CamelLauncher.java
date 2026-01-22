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
package org.apache.camel.dsl.jbang.launcher;

import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;

/**
 * Main class for the Camel JBang Fat-Jar Launcher.
 * <p>
 * This launcher provides a self-contained executable JAR that includes all dependencies required to run Camel JBang
 * without the need for the JBang two-step process.
 */
public class CamelLauncher {

    /**
     * Main entry point for the Camel JBang Fat-Jar Launcher.
     *
     * @param args command line arguments to pass to Camel JBang
     */
    public static void main(String... args) {
        // Set system property to indicate we're running from the launcher
        System.setProperty("camel.launcher", "true");

        // Try to determine and set the JAR path
        String jarPath = detectJarPath();
        if (jarPath != null) {
            System.setProperty("camel.launcher.jar", jarPath);
        }

        CamelJBangMain.run(args);
    }

    private static String detectJarPath() {
        try {
            URL location = CamelLauncher.class.getProtectionDomain()
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
}
