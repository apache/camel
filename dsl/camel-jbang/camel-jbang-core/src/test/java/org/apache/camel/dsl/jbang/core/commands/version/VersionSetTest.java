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
package org.apache.camel.dsl.jbang.core.commands.version;

import java.nio.file.Files;
import java.nio.file.Paths;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.UserConfigHelper;
import org.apache.camel.dsl.jbang.core.commands.config.BaseConfigTestSupport;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.RuntimeType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link VersionSet}.
 *
 * The command persists the selected Camel version, repositories and runtime into the user configuration property file.
 * These tests pin down which keys are written (only the non-null ones), the {@code --reset} behavior, and the choice
 * between the global and local configuration file. Config file cleanup is inherited from {@link BaseConfigTestSupport}.
 */
class VersionSetTest extends BaseConfigTestSupport {

    @Test
    void shouldStoreVersionInGlobalConfig() throws Exception {
        UserConfigHelper.createUserConfig("");

        VersionSet command = new VersionSet(new CamelJBangMain().withPrinter(printer));
        command.version = "4.10.0";
        command.doCall();

        CommandLineHelper.loadProperties(properties -> {
            assertEquals("4.10.0", properties.get("camel-version"));
            // repos and runtime were not requested, so they must not be written
            assertFalse(properties.containsKey("repos"), "repos must not be set when no --repo is given");
            assertFalse(properties.containsKey("runtime"), "runtime must not be set when no --runtime is given");
        });
    }

    @Test
    void shouldStoreVersionRepoAndRuntimeTogether() throws Exception {
        UserConfigHelper.createUserConfig("");

        VersionSet command = new VersionSet(new CamelJBangMain().withPrinter(printer));
        command.version = "4.10.0";
        command.repo = "https://repo.example.org/maven";
        command.runtime = RuntimeType.springBoot;
        command.doCall();

        CommandLineHelper.loadProperties(properties -> {
            assertEquals("4.10.0", properties.get("camel-version"));
            assertEquals("https://repo.example.org/maven", properties.get("repos"));
            // runtime is stored using its canonical runtime() name, not the enum constant
            assertEquals(RuntimeType.springBoot.runtime(), properties.get("runtime"));
        });
    }

    @Test
    void shouldLeaveExistingConfigUntouchedWhenNothingProvided() throws Exception {
        UserConfigHelper.createUserConfig("""
                camel-version=4.9.0
                """);

        VersionSet command = new VersionSet(new CamelJBangMain().withPrinter(printer));
        // no version, repo, runtime or reset: doCall must not change the stored value
        command.doCall();

        CommandLineHelper.loadProperties(properties -> assertEquals("4.9.0", properties.get("camel-version")));
    }

    @Test
    void shouldRemoveAllSettingsOnReset() throws Exception {
        UserConfigHelper.createUserConfig("""
                camel-version=4.9.0
                repos=https://repo.example.org/maven
                runtime=spring-boot
                other=keep-me
                """);

        VersionSet command = new VersionSet(new CamelJBangMain().withPrinter(printer));
        command.reset = true;
        command.doCall();

        CommandLineHelper.loadProperties(properties -> {
            assertFalse(properties.containsKey("camel-version"), "camel-version must be removed on reset");
            assertFalse(properties.containsKey("repos"), "repos must be removed on reset");
            assertFalse(properties.containsKey("runtime"), "runtime must be removed on reset");
            // unrelated settings are preserved: reset only clears the three version keys
            assertEquals("keep-me", properties.get("other"));
        });
    }

    @Test
    void shouldStoreVersionInLocalConfigWhenNotGlobal() throws Exception {
        VersionSet command = new VersionSet(new CamelJBangMain().withPrinter(printer));
        command.version = "4.10.0";
        command.global = false;
        command.doCall();

        // the value lands in the local config file, not the global one
        assertTrue(Files.exists(Paths.get(CommandLineHelper.LOCAL_USER_CONFIG)),
                "local config file should have been created");
        CommandLineHelper.loadProperties(properties -> assertEquals("4.10.0", properties.get("camel-version")), true);
    }

}
