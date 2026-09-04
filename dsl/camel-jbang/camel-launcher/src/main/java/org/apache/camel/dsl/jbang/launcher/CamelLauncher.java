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

import org.apache.camel.dsl.jbang.core.common.LauncherHelper;

/**
 * Main class for the Camel CLI Fat-Jar Launcher.
 * <p>
 * This launcher provides a self-contained executable JAR that includes all dependencies required to run Camel CLI
 * without the need for the JBang two-step process.
 */
public class CamelLauncher {

    /**
     * Main entry point for the Camel CLI Fat-Jar Launcher.
     *
     * @param args command line arguments to pass to Camel CLI
     */
    public static void main(String... args) {
        System.setProperty(LauncherHelper.CAMEL_LAUNCHER_PROPERTY, "true");

        // Resolve JAR path via the shared helper so all downstream code uses one implementation
        String jarPath = LauncherHelper.getLauncherJarPath();
        if (jarPath != null) {
            System.setProperty(LauncherHelper.CAMEL_LAUNCHER_JAR_PROPERTY, jarPath);
        }

        CamelLauncherMain main = new CamelLauncherMain();
        // allow to use 3rd-party plugins
        main.setDiscoverPlugins(true);
        main.execute(args);
    }
}
