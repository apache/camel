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
package org.apache.camel.dsl.jbang.core.commands;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.common.PathUtils;
import org.apache.camel.dsl.jbang.core.common.StringPrinter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WrapperCommandTest {

    private Path workingDir;

    @BeforeEach
    public void beforeEach() throws IOException {
        Path base = Paths.get("target");
        workingDir = Files.createTempDirectory(base, "camel-wrapper");
    }

    @AfterEach
    public void afterEach() throws IOException {
        PathUtils.deleteDirectory(workingDir);
    }

    @Test
    void testWrapperCreatesFiles() throws Exception {
        CamelJBangMain main = new CamelJBangMain();
        StringPrinter printer = new StringPrinter();
        main.setOut(printer);

        WrapperCommand cmd = new WrapperCommand(main);
        cmd.directory = workingDir.toString();
        cmd.camelVersion = "4.10.0";

        int exit = cmd.doCall();

        assertEquals(0, exit);
        assertTrue(Files.exists(workingDir.resolve(".camel/camel-wrapper.properties")));
        assertTrue(Files.exists(workingDir.resolve("camelw")));
        assertTrue(Files.exists(workingDir.resolve("camelw.cmd")));
    }

    @Test
    void testWrapperPropertiesContent() throws Exception {
        CamelJBangMain main = new CamelJBangMain();
        StringPrinter printer = new StringPrinter();
        main.setOut(printer);

        WrapperCommand cmd = new WrapperCommand(main);
        cmd.directory = workingDir.toString();
        cmd.camelVersion = "4.10.0";

        int exit = cmd.doCall();

        assertEquals(0, exit);

        String properties = Files.readString(workingDir.resolve(".camel/camel-wrapper.properties"));
        assertTrue(properties.contains("camel.version=4.10.0"));
        assertTrue(properties.contains("distributionUrl="));
        assertTrue(properties.contains("camel-launcher-4.10.0.jar"));
    }

    @Test
    void testWrapperDefaultsToCurrentVersion() throws Exception {
        CamelJBangMain main = new CamelJBangMain();
        StringPrinter printer = new StringPrinter();
        main.setOut(printer);

        WrapperCommand cmd = new WrapperCommand(main);
        cmd.directory = workingDir.toString();

        int exit = cmd.doCall();

        assertEquals(0, exit);

        CamelCatalog catalog = new DefaultCamelCatalog();
        String expectedVersion = catalog.getCatalogVersion();

        String properties = Files.readString(workingDir.resolve(".camel/camel-wrapper.properties"));
        assertTrue(properties.contains("camel.version=" + expectedVersion));
    }

    @Test
    void testWrapperCustomRepoUrl() throws Exception {
        CamelJBangMain main = new CamelJBangMain();
        StringPrinter printer = new StringPrinter();
        main.setOut(printer);

        WrapperCommand cmd = new WrapperCommand(main);
        cmd.directory = workingDir.toString();
        cmd.camelVersion = "4.10.0";
        cmd.repoUrl = "https://custom.repo.example.com/maven2";

        int exit = cmd.doCall();

        assertEquals(0, exit);

        String properties = Files.readString(workingDir.resolve(".camel/camel-wrapper.properties"));
        assertTrue(properties.contains("https://custom.repo.example.com/maven2"));
    }

    @Test
    void testWrapperScriptContent() throws Exception {
        CamelJBangMain main = new CamelJBangMain();
        StringPrinter printer = new StringPrinter();
        main.setOut(printer);

        WrapperCommand cmd = new WrapperCommand(main);
        cmd.directory = workingDir.toString();
        cmd.camelVersion = "4.10.0";

        int exit = cmd.doCall();

        assertEquals(0, exit);

        String camelw = Files.readString(workingDir.resolve("camelw"));
        assertTrue(camelw.startsWith("#!/bin/sh"));
        assertTrue(camelw.contains("camel-wrapper.properties"));

        String camelwCmd = Files.readString(workingDir.resolve("camelw.cmd"));
        assertTrue(camelwCmd.contains("camel-wrapper.properties"));
    }

    @Test
    void testBuildDistributionUrl() {
        CamelJBangMain main = new CamelJBangMain();
        WrapperCommand cmd = new WrapperCommand(main);
        cmd.camelVersion = "4.10.0";
        cmd.repoUrl = "https://repo1.maven.org/maven2";

        String url = cmd.buildDistributionUrl();
        assertEquals(
                "https://repo1.maven.org/maven2/org/apache/camel/camel-launcher/4.10.0/camel-launcher-4.10.0.jar",
                url);
    }

    @Test
    void testWrapperOutputMessages() throws Exception {
        CamelJBangMain main = new CamelJBangMain();
        StringPrinter printer = new StringPrinter();
        main.setOut(printer);

        WrapperCommand cmd = new WrapperCommand(main);
        cmd.directory = workingDir.toString();
        cmd.camelVersion = "4.10.0";

        int exit = cmd.doCall();

        assertEquals(0, exit);

        String output = printer.getOutput();
        assertTrue(output.contains("Apache Camel wrapper installed successfully."));
        assertTrue(output.contains("Camel version: 4.10.0"));
        assertTrue(output.contains("camelw"));
    }
}
