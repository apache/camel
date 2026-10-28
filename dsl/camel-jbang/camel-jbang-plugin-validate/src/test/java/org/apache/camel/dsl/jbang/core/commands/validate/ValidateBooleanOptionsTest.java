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
package org.apache.camel.dsl.jbang.core.commands.validate;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A boolean option given without a value is on, also for an option that is on by default, such as <tt>camel validate
 * yaml --catalog</tt>.
 */
class ValidateBooleanOptionsTest {

    @Test
    void everyOptionThatIsOnByDefaultIsOnWhenGivenWithoutValue() {
        CamelJBangMain main = new CamelJBangMain();
        CommandLine commandLine = new CommandLine(main);
        new ValidatePlugin().customize(commandLine, main);

        List<String> wrong = new ArrayList<>();
        check(commandLine, wrong);

        assertThat(wrong).as("boolean options that are on by default, without arity = \"0..1\", fallbackValue = \"true\"")
                .isEmpty();
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
