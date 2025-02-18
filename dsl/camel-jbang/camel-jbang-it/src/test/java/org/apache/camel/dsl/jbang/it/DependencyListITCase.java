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

import org.apache.camel.dsl.jbang.it.support.InVersion;
import org.apache.camel.dsl.jbang.it.support.JBangTestSupport;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class DependencyListITCase extends JBangTestSupport {

    @Test
    @InVersion(includeSnapshot = false)
    public void testDependencyList() {
        checkCommandOutputs("dependency list", "org.apache.camel:camel-main:" + version());
        checkCommandOutputs("dependency list --output=maven", "<dependency>\n" +
                                                              "    <groupId>org.apache.camel</groupId>\n" +
                                                              "    <artifactId>camel-main</artifactId>\n" +
                                                              "    <version>" + version() + "</version>\n" +
                                                              "</dependency>");
        checkCommandOutputs("dependency list --runtime=spring-boot",
                "org.apache.camel.springboot:camel-spring-boot-starter:" + version());
    }

    @Test
    @InVersion(includeSnapshot = false)
    public void testCopyingJars() throws IOException {
        Files.createDirectory(Path.of(getDataFolder() + "/deps"));
        execute(String.format("dependency copy --output-directory=%s/deps", mountPoint()));
        Assertions.assertThat(execInHost(String.format("ls %s/deps", getDataFolder())))
                .as("deps directory should contain copied dependency jars")
                .containsPattern("camel-.*" + version() + ".jar");
    }
}
