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

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.aesh.command.Command;
import org.aesh.command.internal.InternalCommandMetadataRegistry;
import org.aesh.command.metadata.CommandMetadataProvider;
import org.aesh.command.metadata.MetadataProviderRegistry;
import org.aesh.command.option.Option;
import org.apache.camel.dsl.jbang.core.commands.process.ListProcess;
import org.apache.camel.dsl.jbang.core.common.CommandTree;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The commands of the CLI have aesh metadata that the aesh annotation processor generates at compile time.
 */
class GeneratedCommandMetadataTest {

    @AfterEach
    void resetRegistries() {
        MetadataProviderRegistry.reset();
    }

    @Test
    void everyCommandHasGeneratedMetadataOfItsOwn() {
        Map<Command<?>, String> commands = new IdentityHashMap<>();
        collect(new CamelJBangMain().newCommandLine(), commands);

        List<String> missing = new ArrayList<>();
        for (Command<?> command : commands.keySet()) {
            String name = command.getClass().getName();
            if (CamelJBangMain.COMMAND_METADATA.excluded().contains(name)) {
                continue;
            }
            CommandMetadataProvider<?> provider = CamelJBangMain.COMMAND_METADATA.get(name);
            if (provider == null || provider.commandType() != command.getClass()) {
                // a command in a package that is not in COMMAND_METADATA, or that the processor did not see
                missing.add(name);
            }
        }

        assertThat(commands).hasSizeGreaterThan(150);
        assertThat(missing).isEmpty();
    }

    @Test
    void theCommandNameComesFromTheMetadata() {
        CommandMetadataProvider<?> provider = CamelJBangMain.COMMAND_METADATA.get(ListProcess.class.getName());

        assertThat(provider.commandName()).isEqualTo("ps");
    }

    @Test
    void excludedAnonymousAndOtherClassesHaveNone() {
        CommandMetadataProvider<?> anonymous = CamelJBangMain.COMMAND_METADATA.get(new ListProcess() {
        }.getClass().getName());

        assertThat(CamelJBangMain.COMMAND_METADATA.get(Export.class.getName())).isNull();
        assertThat(CamelJBangMain.COMMAND_METADATA.get("org.apache.camel.dsl.jbang.core.commands.kubernetes.Some"))
                .isNull();
        assertThat(anonymous).isNull();
    }

    @Test
    void aSubClassWithOptionsOfItsOwnAndNoMetadataFails() {
        MetadataProviderRegistry.useOnly(new InternalCommandMetadataRegistry(), CamelJBangMain.COMMAND_METADATA);

        assertThatThrownBy(() -> CommandTree.checkMetadataOf(new ListProcessWithOption()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ListProcessWithOption.class.getName())
                .hasMessageContaining(ListProcess.class.getName());
        // without options of its own, the metadata of the command it extends is right
        assertThatCode(() -> CommandTree.checkMetadataOf(new ListProcess() {
        })).doesNotThrowAnyException();
    }

    @Test
    void aCommandIsCreatedWithoutTheMainAndGetsItAfterwards() {
        CamelJBangMain main = new CamelJBangMain();

        assertThatThrownBy(() -> new ListProcess().getMain())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ListProcess.class.getName());
        assertThat(main.command(new ListProcess()).getMain()).isSameAs(main);
    }

    private static void collect(CommandTree tree, Map<Command<?>, String> commands) {
        commands.put(tree.getCommand(), "");
        for (CommandTree sub : tree.getSubcommands().values()) {
            collect(sub, commands);
        }
    }

    // a sub class without generated metadata (the tests are compiled without the processor), as a plugin command
    static class ListProcessWithOption extends ListProcess {

        @Option(name = "extra", description = "An option of the sub class")
        String extra;
    }
}
