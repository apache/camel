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

    String currentModel();

    List<String> availableModels();

    /**
     * Switches the active model. Returns {@code false} (without effect) when no LLM client is available to apply the
     * change to.
     */
    boolean switchModel(String model);

    String selectedProcessName();

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
