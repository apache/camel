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
package org.apache.camel.dsl.jbang.core.common;

import java.util.ArrayList;
import java.util.List;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.invocation.CommandInvocation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A command line creates and parses only the sub-command that it runs, and all of them when it lists them.
 */
class CommandTreeLazyTest {

    private final List<String> created = new ArrayList<>();
    private final List<String> ran = new ArrayList<>();

    private CommandTree tree() {
        return new CommandTree(new Parent())
                .addSubcommand("first", CommandTree.lazy(() -> create(new First())))
                .addSubcommand("second", CommandTree.lazy(() -> create(new Second())));
    }

    private Object create(Child child) {
        created.add(child.name());
        return child;
    }

    @Test
    void aSubCommandIsCreatedWhenItRuns() throws Exception {
        CommandTree tree = tree();
        assertThat(created).isEmpty();

        assertThat(tree.execute("second").getResultValue()).isZero();

        assertThat(created).containsExactly("second");
        assertThat(ran).containsExactly("second");
    }

    @Test
    void theHelpListsAllSubCommands() throws Exception {
        StringBuilder help = new StringBuilder();
        CommandTree tree = tree().setOutput(help::append);

        tree.execute("--help");

        assertThat(created).containsExactlyInAnyOrder("first", "second");
        assertThat(help.toString()).contains("first", "second");
        assertThat(ran).isEmpty();
    }

    @Test
    void aNameThatIsNotASubCommandCreatesAllForTheSuggestions() throws Exception {
        CommandTree tree = tree();

        // the CLI prints the error (CamelJBangMain): aesh suggests from all the sub-commands
        assertThatThrownBy(() -> tree.execute("secnd")).hasMessageContaining("Did you mean: second?");

        assertThat(created).containsExactlyInAnyOrder("first", "second");
        assertThat(ran).isEmpty();
    }

    @Test
    void theCommandOfALazyTree() {
        CommandTree tree = tree();
        CommandTree second = tree.getSubcommands().get("second");

        assertThat(second.getName()).isEqualTo("second");
        assertThat(created).isEmpty();
        Object command = second.getCommand();
        assertThat(created).containsExactly("second");
        assertThat(second.getCommand()).isSameAs(command);
        assertThat(CommandTree.of(command)).isSameAs(second);
    }

    @CommandDefinition(name = "parent", description = "The parent", generateHelp = true)
    static class Parent implements Command<CommandInvocation> {
        @Override
        public CommandResult execute(CommandInvocation invocation) {
            return CommandResult.SUCCESS;
        }
    }

    abstract class Child implements Command<CommandInvocation> {
        abstract String name();

        @Override
        public CommandResult execute(CommandInvocation invocation) {
            ran.add(name());
            return CommandResult.SUCCESS;
        }
    }

    @CommandDefinition(name = "first", description = "The first")
    class First extends Child {
        @Override
        String name() {
            return "first";
        }
    }

    @CommandDefinition(name = "second", description = "The second")
    class Second extends Child {
        @Override
        String name() {
            return "second";
        }
    }
}
