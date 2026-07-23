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
package org.apache.camel.dsl.jbang.core.commands.infra;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import org.apache.camel.dsl.jbang.core.commands.CamelCommandBaseTestSupport;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/**
 * Base class for infra command tests that read the {@code infra-<service>-<pid>.json} pid files from the Camel
 * directory. It redirects the home directory to an isolated folder under {@code target} and clears it between tests,
 * following the same convention as {@code ProcessCommandTestSupport}.
 */
abstract class InfraCommandTestSupport extends CamelCommandBaseTestSupport {

    @BeforeEach
    @Override
    public void setup() throws Exception {
        super.setup();
        CommandLineHelper.useHomeDir("target/test-infra-cmd");
        Files.createDirectories(CommandLineHelper.getCamelDir());
    }

    @AfterEach
    public void cleanup() throws Exception {
        Path camelDir = CommandLineHelper.getCamelDir();
        if (Files.exists(camelDir)) {
            try (Stream<Path> walk = Files.walk(camelDir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException e) {
                        // best effort cleanup
                    }
                });
            }
        }
    }

    /**
     * Writes a pid file named {@code infra-<service>-<pid>.json} into the Camel directory with the given JSON content.
     */
    protected static void writePidFile(String service, long pid, String content) throws IOException {
        Path pidFile = CommandLineHelper.getCamelDir().resolve("infra-" + service + "-" + pid + ".json");
        Files.writeString(pidFile, content);
    }
}
