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
package org.apache.camel.dsl.jbang.core.commands.mcp;

import java.util.List;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import io.quarkiverse.mcp.server.Content;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.ToolCallException;
import io.quarkiverse.mcp.server.ToolGuardrails;
import io.quarkiverse.mcp.server.ToolInputGuardrail;
import io.quarkiverse.mcp.server.ToolManager.ToolInfo;
import io.quarkiverse.mcp.server.ToolOutputGuardrail;
import io.quarkiverse.mcp.server.ToolResponse;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import org.apache.camel.dsl.jbang.core.commands.ai.ToolArgumentAliases;

/**
 * The argument names of a tool call (CAMEL-26399), declared with {@link ToolGuardrails} on every tool of the server.
 * Before the server checks the arguments, the names models carry over from another tool are taken for the declared ones
 * ({@link ToolArgumentAliases}: {@code query} for the {@code term} of {@code camel_catalog_find}), and a call that
 * still misses a required argument is answered with the arguments of the tool and the ones it got, where the server
 * would only say {@code Missing required argument: term}. After the call, a JSON object answer gets a note with the
 * declared name of each alias and the arguments the tool ignored, so the next call uses the right names. Other answers,
 * errors included, are left as they are.
 * <p>
 * The note goes from the input to the output through a {@link RequestScoped} bean: the server runs each tool call in a
 * request context of its own. Nothing here blocks, so the guardrail supports every execution model (all tools of the
 * server run on a worker thread).
 */
@Singleton
public class McpArgumentGuardrail implements ToolInputGuardrail, ToolOutputGuardrail {

    @Inject
    CallNote callNote;

    @Override
    public void apply(ToolInputContext context) {
        ToolInfo tool = context.getTool();
        JsonObject arguments = context.getArguments();
        ToolArgumentAliases.Resolved<Object> resolved
                = ToolArgumentAliases.resolve(tool.name(), declared(tool), arguments.getMap());
        if (!resolved.missing().isEmpty()) {
            throw new ToolCallException(resolved.missingMessage());
        }
        if (!resolved.renamed().isEmpty()) {
            context.setArguments(new JsonObject(resolved.arguments()));
        }
        callNote.set(resolved.note());
    }

    @Override
    public void apply(ToolOutputContext context) {
        String note = callNote.take();
        if (note != null) {
            ToolResponse noted = withNote(context.getResponse(), note);
            if (noted != null) {
                context.setResponse(noted);
            }
        }
    }

    /**
     * The arguments of the tool as the server checks them: an argument with a default value is never missing.
     */
    static List<ToolArgumentAliases.Argument> declared(ToolInfo tool) {
        return tool.arguments().stream()
                .map(a -> new ToolArgumentAliases.Argument(a.name(), a.required() && a.defaultValue() == null))
                .toList();
    }

    /**
     * The response with the note as the first field of its JSON object, or null when it is not a successful answer of
     * one JSON object.
     */
    static ToolResponse withNote(ToolResponse response, String note) {
        if (response.isError() || response.structuredContent() != null || response.content() == null
                || response.content().size() != 1) {
            return null;
        }
        Content content = response.content().get(0);
        if (!(content instanceof TextContent text) || !text.text().stripLeading().startsWith("{")) {
            return null;
        }
        JsonObject answer;
        try {
            answer = new JsonObject(text.text());
        } catch (DecodeException e) {
            return null;
        }
        if (answer.containsKey(ToolArgumentAliases.NOTE_KEY)) {
            return null;
        }
        JsonObject noted = new JsonObject().put(ToolArgumentAliases.NOTE_KEY, note);
        answer.forEach(e -> noted.put(e.getKey(), e.getValue()));
        return new ToolResponse(
                false, List.of(new TextContent(noted.encode(), text._meta(), text.annotations())),
                null, response._meta());
    }

    /**
     * The note of the current tool call, from its arguments to its answer. Read through its methods: a field of a
     * request scoped bean is not reached through the client proxy.
     */
    @RequestScoped
    public static class CallNote {

        private String note;

        public void set(String note) {
            this.note = note;
        }

        /** The note, which is then cleared. */
        public String take() {
            String taken = note;
            note = null;
            return taken;
        }
    }
}
