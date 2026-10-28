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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

/** Reads operation contracts asynchronously from the selected running integration. */
final class SemanticCompletions {
    private static final long CACHE_NANOS = TimeUnit.SECONDS.toNanos(5);
    private final MonitorContext ctx;
    private String pid;
    private String expert;
    private long requestedAt;
    private CompletableFuture<JsonObject> pending;

    SemanticCompletions(MonitorContext ctx) {
        this.ctx = ctx;
    }

    CompletableFuture<List<AutocompletePopup.CompletionItem>> provide(SemanticCompletionContext context) {
        IntegrationInfo info = ctx.findSelectedIntegration();
        String runtimePid = info == null ? null : info.phantom ? info.linkedPid : info.pid;
        if (runtimePid == null || runtimePid.isBlank()) {
            reset();
            return CompletableFuture.completedFuture(List.of());
        }
        long now = System.nanoTime();
        if (!Objects.equals(pid, runtimePid) || !Objects.equals(expert, context.expert())
                || pending == null || pending.isDone() && now - requestedAt > CACHE_NANOS) {
            pid = runtimePid;
            expert = context.expert();
            requestedAt = now;
            JsonObject request = new JsonObject();
            request.put("action", "semantic-metadata");
            if (expert != null) {
                request.put("expert", expert);
            }
            String selectedPid = pid;
            pending = CompletableFuture
                    .supplyAsync(() -> ctx.executeIndependentAction(selectedPid, request, 2000), ctx.backgroundExecutor)
                    .exceptionally(failure -> null);
        }
        return pending.thenApply(response -> operations(response, context.shorthand()));
    }

    private List<AutocompletePopup.CompletionItem> operations(JsonObject response, boolean shorthand) {
        if (response == null || !(response.get("operations") instanceof JsonArray operations)) {
            return List.of();
        }
        List<AutocompletePopup.CompletionItem> items = new ArrayList<>();
        for (Object value : operations) {
            if (value instanceof JsonObject operation) {
                String name = operation.getString("name");
                if (name == null || name.isBlank() || shorthand && !name.equals(name.toLowerCase(Locale.ROOT))) {
                    continue;
                }
                String description = operation.getString("description");
                String resultType = operation.getString("resultType");
                items.add(new AutocompletePopup.CompletionItem(
                        name,
                        description + " (returns " + resultType + ")", "string", null, false, null, "semantic"));
            }
        }
        return items;
    }

    void reset() {
        pid = null;
        expert = null;
        pending = null;
    }
}
