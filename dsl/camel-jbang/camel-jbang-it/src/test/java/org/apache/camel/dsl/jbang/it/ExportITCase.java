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

public class ExportITCase extends JBangTestSupport {

    @Test
    public void testExportSB() throws IOException {
        execute(String.format("export --runtime=spring-boot --gav=com.foo:acme:1.0-SNAPSHOT --directory=%s",
                mountPoint()));
        assertFileInDataFolderExists("mvnw");
        assertFileInDataFolderExists("mvnw.cmd");
        assertFileInDataFolderExists("pom.xml");
        assertFileInDataFolderContains("pom.xml", "<groupId>org.apache.camel.springboot</groupId>");
    }

    @Test
    public void testExportQuarkus() throws IOException {
        execute(String.format("export --runtime=quarkus --gav=com.foo:acme:1.0-SNAPSHOT --directory=%s",
                mountPoint()));
        assertFileInDataFolderExists("mvnw");
        assertFileInDataFolderExists("mvnw.cmd");
        assertFileInDataFolderExists("pom.xml");
        assertFileInDataFolderContains("pom.xml", "<groupId>org.apache.camel.quarkus</groupId>");
    }

    @Test
    public void testExportMain() throws IOException {
        execute(String.format("export --runtime=camel-main --gav=com.foo:acme:1.0-SNAPSHOT --directory=%s",
                mountPoint()));
        assertFileInDataFolderExists("mvnw");
        assertFileInDataFolderExists("mvnw.cmd");
        assertFileInDataFolderExists("pom.xml");
        assertFileInDataFolderContains("pom.xml", "<groupId>org.apache.camel</groupId>");
    }

    @Test
    public void testExportFile() throws IOException {
        copyResourceInDataFolder(TestResources.DIR_ROUTE);
        copyResourceInDataFolder(TestResources.SERVER_ROUTE);
        execute(String.format(
                "export %s/FromDirectoryRoute.java %s/server.yaml --runtime=spring-boot --gav=com.foo:acme:1.0-SNAPSHOT --directory=%s",
                mountPoint(), mountPoint(), mountPoint()));
        assertFileInDataFolderExists("src/main/java/com/foo/acme/FromDirectoryRoute.java");
        assertFileInDataFolderExists("src/main/resources/camel/server.yaml");
    }

    @Test
    public void testExportGradle() {
        execute(String.format(
                "export --build-tool=gradle --runtime=spring-boot --gav=com.foo:acme:1.0-SNAPSHOT --directory=%s",
                mountPoint()));
        assertFileInDataFolderExists("gradlew");
        assertFileInDataFolderExists("build.gradle");
        assertFileInDataFolderExists("gradle/wrapper/gradle-wrapper.jar");
    }

    @Test
    public void testExportProperties() throws IOException {
        newFileInDataFolder("application.properties", "camel.jbang.runtime=quarkus");
        execInContainer(String.format("mv %s/application.properties .", mountPoint()));
        execute(String.format(
                "export --gav=com.foo:acme:1.0-SNAPSHOT --directory=%s", mountPoint()));
        assertFileInDataFolderContains("pom.xml", "<groupId>org.apache.camel.quarkus</groupId>");
    }
}
