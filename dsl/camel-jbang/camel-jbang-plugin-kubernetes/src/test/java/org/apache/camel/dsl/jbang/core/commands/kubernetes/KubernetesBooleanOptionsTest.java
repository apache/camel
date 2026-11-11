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
import java.util.LinkedHashSet;
import java.util.List;

import org.aesh.command.impl.internal.ProcessedOption;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CommandTree;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * A boolean option given without a value is on, also for an option that is on by default: <tt>camel kubernetes run
 * --dev --cleanup</tt> removes the deployment when it stops.
 */
class KubernetesBooleanOptionsTest {

    @Test
    void everyOptionThatIsOnByDefaultIsOnWhenGivenWithoutValue() throws Exception {
        CamelJBangMain main = new CamelJBangMain();
        CommandTree commandLine = new CommandTree(main);
        new KubernetesPlugin().customize(commandLine, main);

        List<String> wrong = new ArrayList<>();
        check(commandLine, wrong);

        Assertions.assertEquals(List.of(), wrong,
                "boolean options that need a value");
    }

    @Test
    void cleanupWithoutValue() throws Exception {
        CamelJBangMain main = new CamelJBangMain();
        CommandTree commandLine = new CommandTree(main);
        new KubernetesPlugin().customize(commandLine, main);

        commandLine.parse("kubernetes", "run", "--dev", "--cleanup", "route.yaml");
        KubernetesRun run = (KubernetesRun) commandLine.getSubcommands().get("kubernetes").getSubcommands().get("run")
                .getCommand();

        Assertions.assertTrue(run.cleanup);
        // the file after the flag is not taken as its value
        Assertions.assertEquals(List.of("route.yaml"), run.files);
    }

    private static void check(CommandTree commandLine, List<String> wrong) throws Exception {
        for (CommandTree sub : new LinkedHashSet<>(commandLine.getSubcommands().values())) {
            for (ProcessedOption option : sub.processedCommand().getOptions()) {
                boolean flag = option.type() == boolean.class || option.type() == Boolean.class;
                if (flag && option.hasValue() && !option.isOptionalValue()) {
                    wrong.add(sub.getQualifiedName() + " --" + option.name());
                }
            }
            check(sub, wrong);
        }
    }
}
