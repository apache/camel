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

        assertEquals(List.of("help", "provider", "model", "clear", "close", "exit", "quit", "run", "infra", "send"),
                registry.descriptors().stream().map(AiSlashCommandRegistry.Descriptor::name).toList());
    }

    @Test
    void lookupIsCaseInsensitiveAndResolvesAliases() {
        AiSlashCommandRegistry registry = AiSlashCommandRegistry.defaults();

        assertEquals("help", registry.lookup("HELP").orElseThrow().name());
        assertEquals("provider", registry.lookup("p").orElseThrow().name());
        assertEquals("model", registry.lookup("m").orElseThrow().name());
        assertEquals("quit", registry.lookup("q").orElseThrow().name());
    }

    @Test
    void rootSlashCommandResolvesToHelp() {
        AiSlashCommandRegistry registry = AiSlashCommandRegistry.defaults();

        assertEquals("help", registry.parse("/").orElseThrow().descriptor().name());
    }

    @Test
    void helpTextComesFromDescriptors() {
        String help = AiSlashCommandRegistry.defaults().helpText();

        assertTrue(help.contains("/run <camel run args>"));
        assertTrue(help.contains("/send <endpoint> <message text | @file>"));
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
}
