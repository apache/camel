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
package org.apache.camel.cli.connector;

import java.util.concurrent.CompletableFuture;

import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * How an action turns into a reply, the same for the file and the WebSocket transport.
 */
class CliActionOutcomeTest {

    @Test
    void resultWithoutFailure() {
        CliActionOutcome outcome = outcome(result("{\"status\":\"success\",\"value\":1}"));

        assertThat(outcome.error()).isNull();
        assertThat(outcome.result()).containsEntry("status", "success");
    }

    @Test
    void failedResultTakesTheMessageFromTheResult() {
        assertThat(outcome(result("{\"status\":\"error\",\"exception\":{\"message\":\"Forced\"}}")).error())
                .isEqualTo("Forced");
        assertThat(outcome(result("{\"status\":\"failed\",\"error\":\"Busy\"}")).error()).isEqualTo("Busy");
        assertThat(outcome(result("{\"status\":\"error\",\"message\":\"No such datasource\"}")).error())
                .isEqualTo("No such datasource");
        assertThat(outcome(result("{\"status\":\"error\"}")).error()).isEqualTo("Action failed");
    }

    @Test
    void unknownAction() {
        CliActionOutcome outcome = join(CliActionOutcome.dispatch((action, output) -> false, action("nope")));

        assertThat(outcome.error()).isEqualTo("Unknown action: nope");
        assertThat(outcome.errorReply().toJson()).isEqualTo("{\"status\":\"failed\",\"error\":\"Unknown action: nope\"}");
    }

    @Test
    void exception() {
        CliActionOutcome outcome = join(CliActionOutcome.dispatch((action, output) -> {
            throw new IllegalArgumentException("Bad input");
        }, action("eval")));

        assertThat(outcome.error()).isEqualTo("Bad input");
        assertThat(outcome.failure()).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void notFoundHasItsReason() {
        CliActionOutcome outcome = join(CliActionOutcome.dispatch((action, output) -> {
            output.notFound("No route matching: nope");
            return true;
        }, action("route")));

        assertThat(outcome.error()).isEqualTo("No route matching: nope");
        assertThat(outcome.reason()).isEqualTo(CliActionOutcome.NOT_FOUND);
        assertThat(outcome.errorReply()).containsEntry("reason", "not-found");
    }

    @Test
    void cancellingTheOutcomeCancelsTheAction() {
        CompletableFuture<Boolean> running = new CompletableFuture<>();
        CliActionDispatcher dispatcher = new CliActionDispatcher() {
            @Override
            public boolean dispatch(JsonObject action, CliActionOutput output) {
                return true;
            }

            @Override
            public CompletableFuture<Boolean> dispatchAsync(JsonObject action, CliActionOutput output) {
                return running;
            }
        };

        CompletableFuture<CliActionOutcome> outcome = CliActionOutcome.dispatch(dispatcher, action("slow"));
        outcome.cancel(true);

        assertThat(running).isCancelled();
    }

    private static CliActionOutcome outcome(JsonObject result) {
        return join(CliActionOutcome.dispatch((action, output) -> {
            output.write(result);
            return true;
        }, action("test")));
    }

    private static CliActionOutcome join(CompletableFuture<CliActionOutcome> future) {
        return future.join();
    }

    private static JsonObject result(String json) {
        return (JsonObject) Jsoner.deserialize(json, (JsonObject) null);
    }

    private static JsonObject action(String name) {
        JsonObject jo = new JsonObject();
        jo.put("action", name);
        return jo;
    }
}
