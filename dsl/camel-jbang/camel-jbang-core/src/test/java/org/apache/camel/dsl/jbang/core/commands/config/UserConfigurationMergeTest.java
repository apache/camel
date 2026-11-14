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
package org.apache.camel.dsl.jbang.core.commands.config;

import java.util.Properties;

import org.apache.camel.dsl.jbang.core.commands.UserConfigHelper;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.run.common.StringPrinter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The local ./camel-cli.properties is laid over the global ~/.camel-cli.properties: before, it hid the whole global
 * file, such as its repos (CAMEL-26931).
 */
class UserConfigurationMergeTest extends BaseConfigTestSupport {

    @Test
    void theLocalFileIsLaidOverTheGlobalOne() throws Exception {
        UserConfigHelper.createUserConfig("repos=https://repo.acme.com/maven\ncamel-version=4.22.0\nruntime=main\n");
        UserConfigHelper.createUserConfig("runtime=quarkus\n", true);

        Properties config = CommandLineHelper.loadUserConfiguration(printer);

        // the global keys the local file does not have stay
        assertThat(config.getProperty("repos")).isEqualTo("https://repo.acme.com/maven");
        assertThat(config.getProperty("camel-version")).isEqualTo("4.22.0");
        // a key of the local file wins
        assertThat(config.getProperty("runtime")).isEqualTo("quarkus");
    }

    @Test
    void theGlobalFileAloneIsTheConfiguration() throws Exception {
        UserConfigHelper.createUserConfig("runtime=spring-boot\n");

        assertThat(CommandLineHelper.loadUserConfiguration(printer)).containsEntry("runtime", "spring-boot")
                .hasSize(1);
    }

    @Test
    void aLocalFileThatCannotBeParsedLeavesTheGlobalOne() throws Exception {
        UserConfigHelper.createUserConfig("repos=https://repo.acme.com/maven\n");
        // a malformed unicode escape, such as a Windows path written by hand
        UserConfigHelper.createUserConfig("path=C:\\users\\name\n", true);
        StringPrinter warnings = new StringPrinter();

        Properties config = CommandLineHelper.loadUserConfiguration(warnings);

        assertThat(config).containsEntry("repos", "https://repo.acme.com/maven").doesNotContainKey("path");
        assertThat(warnings.getStderr()).contains("could not parse").contains(CommandLineHelper.LOCAL_USER_CONFIG);
    }
}
