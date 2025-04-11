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
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.camel.dsl.jbang.it.support.JBangTestSupport;
import org.junit.jupiter.api.Test;

public class MavenGradleITCase extends JBangTestSupport {

    @Test
    public void runFromMavenModuleTest() {
        execInContainer(String.format("mkdir %s/mvn-app", mountPoint()));
        execInContainer(String.format("cd %s/mvn-app && camel init cheese.xml", mountPoint()));
        execInContainer(String.format(
                "cd %s/mvn-app && camel export --runtime=camel-main --gav=org.jbang:maven-app:1.0-SNAPSHOT", mountPoint()));
        execInContainer(String.format("cd %s/mvn-app && camel run pom.xml --background", mountPoint()));
        checkLogContains("Apache Camel " + version() + " (maven-app) started");
        checkLogContains("Hello Camel from route1");
    }

    @Test
    public void runFromGradleTest() throws IOException {
        execInContainer(String.format("mkdir %s/gradle-app", mountPoint()));
        execInContainer(String.format("cd %s/gradle-app && camel init cheese.xml", mountPoint()));
        execInContainer(String.format(
                "cd %s/gradle-app && camel export --runtime=camel-main --gav=org.jbang:gradle-app:1.0-SNAPSHOT", mountPoint()));
        copyResourceInDataFolder(TestResources.BUILD_GRADLE);
        Files.move(Path.of(String.format("%s/build.gradle", getDataFolder())),
                Path.of(String.format("%s/gradle-app/build.gradle", getDataFolder())));
        execInContainer(String.format("cd %s/gradle-app && camel run pom.xml --background", mountPoint()));
        checkLogContains("Apache Camel " + version() + " (gradle-app) started");
        checkLogContains("Hello Camel from route1");
    }
}
