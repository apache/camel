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
package org.apache.camel.dsl.jbang.core.commands.kubernetes;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;

/**
 * A boolean option given without a value is on, also for an option that is on by default: <tt>camel kubernetes run
 * --dev --cleanup</tt> removes the deployment when it stops.
 */
class KubernetesBooleanOptionsTest {

    @Test
    void everyOptionThatIsOnByDefaultIsOnWhenGivenWithoutValue() {
        CamelJBangMain main = new CamelJBangMain();
        CommandLine commandLine = new CommandLine(main);
        new KubernetesPlugin().customize(commandLine, main);

        List<String> wrong = new ArrayList<>();
        check(commandLine, wrong);

        Assertions.assertEquals(List.of(), wrong,
                "boolean options that are on by default, without arity = \"0..1\", fallbackValue = \"true\"");
    }

    @Test
    void cleanupWithoutValue() {
        CamelJBangMain main = new CamelJBangMain();
        CommandLine commandLine = new CommandLine(main);
        new KubernetesPlugin().customize(commandLine, main);

        CommandLine.ParseResult result = commandLine.parseArgs("kubernetes", "run", "--dev", "--cleanup", "route.yaml");
        CommandSpec run = result.subcommand().subcommand().commandSpec();

        Assertions.assertEquals(Boolean.TRUE, run.findOption("--cleanup").getValue());
        // the file after the flag is not taken as its value
        Assertions.assertEquals(List.of("route.yaml"), ((KubernetesRun) run.userObject()).files);
    }

    private static void check(CommandLine commandLine, List<String> wrong) {
        CommandSpec spec = commandLine.getCommandSpec();
        for (OptionSpec option : spec.options()) {
            boolean flag = option.type() == boolean.class || option.type() == Boolean.class;
            boolean onByDefault = "true".equals(option.defaultValue()) || Boolean.TRUE.equals(option.initialValue());
            if (flag && onByDefault && !(option.arity().min() == 0 && "true".equals(option.fallbackValue()))) {
                wrong.add(spec.qualifiedName(" ") + " " + option.longestName());
            }
        }
        commandLine.getSubcommands().values().forEach(sub -> check(sub, wrong));
    }
}
