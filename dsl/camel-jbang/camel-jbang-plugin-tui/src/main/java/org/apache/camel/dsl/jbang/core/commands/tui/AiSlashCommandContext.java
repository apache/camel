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
import java.util.concurrent.CompletableFuture;

interface AiSlashCommandContext {
    void closePanel();

    void requestExit();

    void openProviderSwitch();

    void clearConversation();

    void clearHistory();

    String currentModel();

    List<String> availableModels();

    /**
     * Switches the active model. Returns {@code false} (without effect) when no LLM client is available to apply the
     * change to.
     */
    boolean switchModel(String model);

    String selectedProcessName();

    /**
     * Describes the tool set currently sent to the model, for example {@code core (18 of 46 tools), mode auto}.
     */
    String describeToolMode();

    /** How file writes by the model are handled: {@code confirm} (dialog for every write) or {@code auto}. */
    default String describeWriteMode() {
        return "confirm";
    }

    /** Switches the write mode; returns false for an unknown mode. */
    default boolean switchWriteMode(String mode) {
        return false;
    }

    /**
     * Multi-line summary of what the next request will cost: provider and model, tool set, static prefix size,
     * conversation history size and the session total so far.
     */
    String describeContext();

    /**
     * Compacts the model history now (older tool results shrunk, oldest turns dropped) and returns a one-line summary
     * of the effect.
     */
    String compactHistoryNow();

    /**
     * Resends the last question. Returns {@code false} when there is no question to retry or no client to send it to.
     */
    boolean retryLastQuestion();

    /**
     * Text summary of the AI usage so far (requests, tokens, latency, per model), the same figures the Ctrl+U view
     * shows.
     */
    String usageSummary();

    /** Clears the usage statistics so the summary and the Ctrl+U view start from zero. */
    void resetUsage();

    void copyLastResponse();

    void exportConversation();

    /**
     * The system prompt the panel sends with every request.
     */
    String systemPrompt();

    /**
     * Switches the tool mode to {@code auto}, {@code core} or {@code full} and persists it. Returns {@code false} when
     * the mode is not one of those values.
     */
    boolean switchToolMode(String mode);

    CompletableFuture<AiCliCommandExecutor.Result> executeCli(AiCliCommandExecutor.Request request);

    void cancelCli();

    /**
     * Launches a long-running command (such as {@code camel run} or {@code camel infra run}) as a detached, tracked
     * background process, first starting any infra services the launch requires. Returns a human-readable status line
     * to show in the panel, or throws a {@link RuntimeException} whose message describes why the launch could not
     * start.
     */
    String launchDetached(AiSlashCommandRegistry.LaunchSpec spec);
}
