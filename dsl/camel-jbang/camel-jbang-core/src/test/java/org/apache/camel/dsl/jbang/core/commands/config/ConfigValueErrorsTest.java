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

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.UserConfigHelper;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.CommandTree;
import org.apache.camel.dsl.jbang.run.common.StringPrinter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A value of the user configuration that an option does not take (CAMEL-26931): every command with the option failed
 * with "DefaultValueProvider failed for option runtime", and camel config set stored it without a word.
 */
class ConfigValueErrorsTest extends BaseConfigTestSupport {

    private final StringPrinter out = new StringPrinter();

    private int run(String... args) {
        CamelJBangMain main = new CamelJBangMain().withPrinter(out);
        CommandTree commandLine = main.newCommandLine();
        CommandLineHelper.augmentWithUserConfiguration(commandLine, out);
        return main.run(commandLine, args);
    }

    @Test
    void aBadValueNamesTheFileTheKeyAndTheValue() throws Exception {
        UserConfigHelper.createUserConfig("runtime=springboot\n");

        assertThat(run("catalog", "component")).isEqualTo(CliExitCode.USAGE);
        assertThat(out.getStderr())
                .contains("Invalid value in " + CommandLineHelper.getHomeDir().resolve(CommandLineHelper.USER_CONFIG)
                          + ": runtime=springboot")
                .contains("camel config unset runtime")
                .doesNotContain("DefaultValueProvider failed");
    }

    @Test
    void configSetRefusesAValueTheOptionDoesNotTake() throws Exception {
        UserConfigHelper.createUserConfig("");

        assertThat(run("config", "set", "runtime=springboot")).isEqualTo(CliExitCode.USAGE);
        assertThat(out.getStderr()).contains("Invalid value runtime=springboot");
        CommandLineHelper.loadProperties(properties -> assertThat(properties).doesNotContainKey("runtime"));

        assertThat(run("config", "set", "runtime=spring-boot")).isEqualTo(CliExitCode.OK);
        CommandLineHelper.loadProperties(properties -> assertThat(properties).containsEntry("runtime", "spring-boot"));
    }

    @Test
    void configSetWarnsOfAKeyNoCommandHas() throws Exception {
        UserConfigHelper.createUserConfig("");

        assertThat(run("config", "set", "nope=1")).isEqualTo(CliExitCode.OK);
        assertThat(out.getStderr()).contains("no command of the CLI has the option nope");
        CommandLineHelper.loadProperties(properties -> assertThat(properties).containsEntry("nope", "1"));
    }
}
