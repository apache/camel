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
package org.apache.camel.dsl.jbang.it;

import java.io.IOException;

import org.apache.camel.dsl.jbang.it.support.JBangTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperty(named = "cli.service.camel.launcher", matches = ".*camel-launcher.*",
                         disabledReason = "Disabled unless link to camel-launcher is specified")
public class CamelLauncherITCase extends JBangTestSupport {

    @Test
    public void camelLauncherSmokeTest() throws IOException {
        copyResourceInDataFolder(TestResources.DIR_ROUTE);
        execInContainer(String.format(
                "curl --output %s/camel-launcher.jar " + getCamelLauncher(),
                mountPoint()));
        execInContainer(String.format("java -jar %s/camel-launcher.jar run %s/FromDirectoryRoute.java --background",
                mountPoint(), mountPoint()));
        checkLogContains("Hello world!");
    }
}
