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

import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.StringPrinter;
import org.apache.camel.util.FileUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

public class CamelCommandBaseTestSupport {

    protected StringPrinter printer;

    private Path previousHome;
    private Path testHome;

    @BeforeEach
    public void setup() throws Exception {
        printer = new StringPrinter();
    }

    /**
     * Gives the test a home directory of its own: an empty directory for the {@code .camel} directory, the user
     * configuration and the plugins of the CLI. The tests run in parallel forks, so a fixed directory would be shared
     * with the tests of the other forks. The previous home is used again after the test.
     *
     * @return the home directory
     */
    protected Path useTestHomeDir() throws IOException {
        if (previousHome == null) {
            previousHome = CommandLineHelper.getHomeDir();
        }
        testHome = Files.createTempDirectory("camel-cli-test-home");
        CommandLineHelper.useHomeDir(testHome.toString());
        return testHome;
    }

    @AfterEach
    public void restoreHomeDir() {
        if (previousHome != null) {
            CommandLineHelper.useHomeDir(previousHome.toString());
            previousHome = null;
        }
        if (testHome != null) {
            FileUtil.removeDir(testHome.toFile());
            testHome = null;
        }
    }

    public static String quarkusExtRegistry() {
        return "--quarkus-ext-registry="
               + Path.of("target/test-classes/registry.quarkus.io").toAbsolutePath().normalize().toUri().toString();
    }

}
