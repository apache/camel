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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiSlashCommandRegistryTest {

    @Test
    void descriptorsKeepStableOrder() {
        AiSlashCommandRegistry registry = AiSlashCommandRegistry.defaults();

        assertEquals(List.of("help", "provider", "model", "clear", "close", "quit", "run", "infra", "send"),
                registry.descriptors().stream().map(AiSlashCommandRegistry.Descriptor::name).toList());
    }

    @Test
    void lookupIsCaseInsensitiveAndResolvesAliases() {
        AiSlashCommandRegistry registry = AiSlashCommandRegistry.defaults();

        assertEquals("help", registry.lookup("HELP").orElseThrow().name());
        assertEquals("provider", registry.lookup("p").orElseThrow().name());
        assertEquals("model", registry.lookup("m").orElseThrow().name());
        assertEquals("quit", registry.lookup("q").orElseThrow().name());
        assertEquals("quit", registry.lookup("exit").orElseThrow().name());
        assertEquals("quit", registry.lookup("x").orElseThrow().name());
    }

    @Test
    void rootSlashCommandResolvesToHelp() {
        AiSlashCommandRegistry registry = AiSlashCommandRegistry.defaults();

        assertEquals("help", registry.parse("/").orElseThrow().descriptor().name());
    }

    @Test
    void helpTextComesFromDescriptors() {
        AiSlashCommandRegistry registry = AiSlashCommandRegistry.defaults();
        String help = registry.helpText();
        AiSlashCommandRegistry.Descriptor provider = registry.lookup("provider").orElseThrow();
        int width = AiSlashCommandRegistry.commandColumnWidth(registry.descriptors());

        assertTrue(help.contains("/run <camel run args>"));
        assertTrue(help.contains("/send <endpoint> <message text | @file>"));
        assertTrue(help.contains(AiSlashCommandRegistry.formatAlignedLine(provider, width)));
        assertFalse(help.contains("\n\n/"));
    }

    @Test
    void completionsIncludeAllCommandsForBareSlash() {
        AiSlashCommandRegistry registry = AiSlashCommandRegistry.defaults();

        assertEquals(9, registry.completionsFor("/").size());
        assertFalse(registry.completionsFor("/").stream().anyMatch(descriptor -> "exit".equals(descriptor.name())));
    }

    @Test
    void completionsFilterByPrefix() {
        AiSlashCommandRegistry registry = AiSlashCommandRegistry.defaults();

        assertEquals("help", registry.completionsFor("/h").get(0).name());
        assertTrue(registry.completionsFor("/p").stream().anyMatch(descriptor -> "provider".equals(descriptor.name())));
    }

    @Test
    void completionsHideAfterKnownCommandWithTrailingSpace() {
        AiSlashCommandRegistry registry = AiSlashCommandRegistry.defaults();

        assertTrue(registry.completionsFor("/send ").isEmpty());
    }

    @Test
    void placeholderUsesRegistryDescriptor() {
        AiSlashCommandRegistry registry = AiSlashCommandRegistry.defaults();

        assertEquals("<files...> [--dev] [--port=8080] [...]", registry.placeholderFor("/run ").orElseThrow());
        assertEquals("<endpoint> <message text | @file>", registry.placeholderFor("/send ").orElseThrow());
        assertTrue(registry.placeholderFor("/run route.yaml").isEmpty());
    }

    @Test
    void sendParserHandlesLiteralBody() {
        AiSlashCommandRegistry.SendCommand command = AiSlashCommandRegistry.parseSend("direct:foo hello world");

        assertEquals("direct:foo", command.endpoint());
        assertEquals("hello world", command.body());
        assertFalse(command.fileBody());
    }

    @Test
    void sendParserHandlesWholeFileBody() {
        AiSlashCommandRegistry.SendCommand command = AiSlashCommandRegistry.parseSend("direct:foo @payload.json");

        assertEquals("direct:foo", command.endpoint());
        assertEquals("payload.json", command.body());
        assertTrue(command.fileBody());
    }

    @Test
    void sendParserDoesNotExpandInlineFileReference() {
        AiSlashCommandRegistry.SendCommand command
                = AiSlashCommandRegistry.parseSend("direct:foo prefix @payload.json suffix");

        assertEquals("prefix @payload.json suffix", command.body());
        assertFalse(command.fileBody());
    }

    @Test
    void sendParserDoesNotExpandFileReferenceWithNewlineBody() {
        AiSlashCommandRegistry.SendCommand command = AiSlashCommandRegistry.parseSend("direct:foo @payload.json\nextra");

        assertEquals("@payload.json\nextra", command.body());
        assertFalse(command.fileBody());
    }

    @Test
    void sendParserRejectsMissingEndpointAndBody() {
        assertEquals("Missing endpoint. Usage: /send <endpoint> <message text | @file>",
                assertThrows(IllegalArgumentException.class, () -> AiSlashCommandRegistry.parseSend("")).getMessage());
        assertEquals("Missing body. Usage: /send <endpoint> <message text | @file>",
                assertThrows(IllegalArgumentException.class, () -> AiSlashCommandRegistry.parseSend("direct:foo"))
                        .getMessage());
    }

    @Test
    void unknownCommandIncludesHelpHint() {
        AiSlashCommandRegistry.CommandResult result
                = AiSlashCommandRegistry.defaults().execute("/nope", new NoopSlashContext());

        assertEquals(AiRole.ERROR, result.role());
        assertEquals("Unknown command: /nope. Type /help for available commands.", result.text());
    }

    @Test
    void helpCommandReflectsThisRegistrysOwnDescriptorsWithoutRebuilding() {
        AiSlashCommandRegistry registry = AiSlashCommandRegistry.defaults();

        AiSlashCommandRegistry.CommandResult result = registry.execute("/help", new NoopSlashContext());

        assertEquals(AiRole.SYSTEM, result.role());
        assertEquals(registry.helpText(), result.text());
    }

    @Test
    void constructorRejectsDuplicateNameOrAliasAcrossCommands() {
        List<AiSlashCommandRegistry.Descriptor> commands = List.of(
                new AiSlashCommandRegistry.Descriptor(
                        "foo", List.of("x"), "Foo command", null,
                        (context, arguments) -> AiSlashCommandRegistry.CommandResult.system("foo")),
                new AiSlashCommandRegistry.Descriptor(
                        "bar", List.of("x"), "Bar command", null,
                        (context, arguments) -> AiSlashCommandRegistry.CommandResult.system("bar")));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> AiSlashCommandRegistry.forTesting(commands));
        assertTrue(ex.getMessage().contains("x"));
    }

    @Test
    void executeCatchesUnexpectedRuntimeExceptionFromExecutor() {
        List<AiSlashCommandRegistry.Descriptor> commands = List.of(
                new AiSlashCommandRegistry.Descriptor(
                        "boom", List.of(), "Throws", null,
                        (context, arguments) -> {
                            throw new IllegalStateException("kaboom");
                        }));
        AiSlashCommandRegistry registry = AiSlashCommandRegistry.forTesting(commands);

        AiSlashCommandRegistry.CommandResult result = registry.execute("/boom", new NoopSlashContext());

        assertEquals(AiRole.ERROR, result.role());
        assertEquals("kaboom", result.text());
    }

    private static final class NoopSlashContext implements AiSlashCommandContext {

        @Override
        public void closePanel() {
        }

        @Override
        public void requestExit() {
        }

        @Override
        public void openProviderSwitch() {
        }

        @Override
        public void clearConversation() {
        }

        @Override
        public String currentModel() {
            return "test-model";
        }

        @Override
        public List<String> availableModels() {
            return List.of();
        }

        @Override
        public boolean switchModel(String model) {
            return true;
        }

        @Override
        public String selectedProcessName() {
            return null;
        }

        @Override
        public java.util.concurrent.CompletableFuture<AiCliCommandExecutor.Result> executeCli(
                AiCliCommandExecutor.Request request) {
            return java.util.concurrent.CompletableFuture.completedFuture(
                    new AiCliCommandExecutor.Result("", 0, "", 0, false));
        }

        @Override
        public void cancelCli() {
        }
    }
}
