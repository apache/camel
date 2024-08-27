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

    final public void generateArchetype() {
        execInHost("mvn archetype:generate" +
                   "  -DarchetypeGroupId=org.apache.camel.archetypes" +
                   "  -DarchetypeArtifactId=camel-archetype-java" +
                   "  -DinteractiveMode=false" +
                   "  -DgroupId=org.jbang -DartifactId=jbang-app -Dversion=1.0-SNAPSHOT " +
                   "  -DoutputDirectory=" + getDataFolder() +
                   "  -DarchetypeVersion=" + version());
    }

    @Test
    public void runFromMavenModuleTest() {
        generateArchetype();
        executeBackground(String.format("run %s/jbang-app/pom.xml", mountPoint()));
        checkLogContains("Apache Camel " + version() + " (CamelJBang) started");
    }

    @Test
    public void runFromGradleTest() throws IOException {
        generateArchetype();
        copyResourceInDataFolder(TestResources.BUILD_GRADLE);
        Files.move(Path.of(String.format("%s/build.gradle", getDataFolder())),
                Path.of(String.format("%s/jbang-app/build.gradle", getDataFolder())));
        executeBackground(String.format("run %s/jbang-app/build.gradle", mountPoint()));
        checkLogContains("Apache Camel " + version() + " (CamelJBang) started");
    }
}
