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
package org.apache.camel.test.infra.cli.it;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junitpioneer.jupiter.ReadsSystemProperty;
import org.junitpioneer.jupiter.RestoreSystemProperties;
import org.junitpioneer.jupiter.SetSystemProperty;

@RestoreSystemProperties
public class CliConfigITCase extends AbstractTestSupport {

    @Test
    @SetSystemProperty(key = "cli.service.version", value = "4.16.0")
    @DisabledIf("isLocalProcessWithSkipInstall")
    public void setJBangVersionTest() {
        execute(cliService -> {
            String version = cliService.version();
            Assertions.assertEquals("4.16.0", version, "Check specific Camel JBang version");
        });
    }

    @Test
    @SetSystemProperty(key = "cli.service.execute.version", value = "4.14.2")
    public void setCamelVersionTest() {
        execute(cliService -> {
            String version = cliService.version();
            Assertions.assertEquals("4.14.2", version, "Check specific Camel version");
        });
    }

    @Test
    @SetSystemProperty(key = "cli.service.branch", value = "camel-4.4.x")
    @DisabledIf("isLocalProcessWithSkipInstall")
    public void setBranchTest() {
        execute(cliService -> {
            String version = cliService.version();
            Assertions.assertEquals("4.4.3", version, "Check Camel JBang version in a specific branch");
        });
    }

    @Test
    @SetSystemProperty(key = "cli.service.repo", value = "mcarlett/apache-camel")
    @SetSystemProperty(key = "cli.service.branch", value = "camel-cli-test")
    @DisabledIf("isLocalProcessWithSkipInstall")
    public void setRepoTest() {
        execute(cliService -> {
            String version = cliService.version();
            Assertions.assertEquals("4.9.0", version, "Check Camel JBang version in a specific repository");
        });
    }

    @Test
    @ReadsSystemProperty
    @EnabledIfSystemProperty(named = "currentProjectVersion", matches = "^(?!\\s*$).+",
                             disabledReason = "currentProjectVersion system property must be set")
    @DisabledIf("isLocalProcessWithSkipInstall")
    public void setCurrentProjectVersionTest() {
        String currentCamelVersion = System.getProperty("currentProjectVersion");
        System.setProperty("cli.service.version", currentCamelVersion);
        execute(cliService -> {
            String version = cliService.version();
            Assertions.assertEquals(currentCamelVersion, version, "Check Camel JBang version in the current codebase");
        });
        System.clearProperty("cli.service.version");
    }

    @Test
    @SetSystemProperty(key = "cli.service.mvn.local", value = "target/tmp-repo")
    @DisabledIfSystemProperty(named = "camel-cli.instance.type", matches = "local-camel-cli-process",
                              disabledReason = "Test not supported with local process service")
    public void setLocalMavenRepoTest() {
        final Path dir = Path.of("target/tmp-repo");
        execute(cliService -> {
            cliService.execute("init foo.yaml");
            cliService.execute("run foo.yaml --max-seconds=5");
            Assertions.assertTrue(dir.toFile().exists(), "Check the local maven repository is created");
            try {
                Assertions.assertTrue(Files.list(dir).findFirst().isPresent(),
                        "Check the local maven repository is not empty");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
