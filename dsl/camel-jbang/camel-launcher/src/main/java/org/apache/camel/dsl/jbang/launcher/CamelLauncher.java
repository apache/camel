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
        CamelJBangMain.run(args);
    }
}
