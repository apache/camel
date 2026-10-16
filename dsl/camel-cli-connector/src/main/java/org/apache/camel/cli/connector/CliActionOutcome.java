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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.apache.camel.util.json.JsonObject;

/**
 * The outcome of an action: its result, and the error when it failed. Both transports reply with it, so a tool gets the
 * same answer whether it uses files or a WebSocket.
 * <p/>
 * An action failed when it threw an exception, is unknown, reported an error to {@link CliActionOutput}, or wrote a
 * result with the status <tt>error</tt> or <tt>failed</tt>.
 */
final class CliActionOutcome {

    /**
     * The reason of an error about something that does not exist, see {@link CliActionOutput#notFound(String)}.
     */
    static final String NOT_FOUND = "not-found";

    private final JsonObject result;
    private final String error;
    private final String reason;
    private final Throwable failure;
    private final boolean failedResult;

    private CliActionOutcome(JsonObject result, String error, String reason, Throwable failure, boolean failedResult) {
        this.result = result;
        this.error = error;
        this.reason = reason;
        this.failure = failure;
        this.failedResult = failedResult;
    }

    /**
     * An action that could not be dispatched, such as an action file that is not valid JSON.
     */
    static CliActionOutcome failed(String error) {
        return new CliActionOutcome(null, error, null, null, false);
    }

    /**
     * Dispatches the action. The future completes with the outcome, and cancelling it interrupts the action.
     */
    static CompletableFuture<CliActionOutcome> dispatch(CliActionDispatcher dispatcher, JsonObject action) {
        Collector output = new Collector();
        CompletableFuture<Boolean> completion;
        try {
            completion = dispatcher.dispatchAsync(action, output);
        } catch (Exception e) {
            completion = CompletableFuture.failedFuture(e);
        }
        CompletableFuture<Boolean> dispatched = completion;
        CompletableFuture<CliActionOutcome> answer
                = dispatched.handle((known, failure) -> of(action, output, known, failure));
        answer.whenComplete((value, failure) -> {
            if (answer.isCancelled()) {
                dispatched.cancel(true);
            }
        });
        return answer;
    }

    private static CliActionOutcome of(JsonObject action, Collector output, Boolean known, Throwable failure) {
        if (failure != null) {
            Throwable cause = failure;
            while (cause instanceof CompletionException && cause.getCause() != null) {
                cause = cause.getCause();
            }
            String message = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
            return new CliActionOutcome(output.result, message, null, cause, false);
        }
        if (Boolean.FALSE.equals(known)) {
            String name = action.getString("action");
            return new CliActionOutcome(
                    output.result, name != null ? "Unknown action: " + name : "Missing action", null, null, false);
        }
        String reported = output.error();
        if (reported != null) {
            return new CliActionOutcome(output.result, reported, output.reason(), null, false);
        }
        if (output.result != null) {
            // many actions report failures inside their result, as the file transport had no other way to do it
            String status = output.result.getString("status");
            if ("error".equals(status) || "failed".equals(status)) {
                return new CliActionOutcome(output.result, messageOf(output.result), null, null, true);
            }
        }
        return new CliActionOutcome(output.result, null, null, null, false);
    }

    private static String messageOf(JsonObject result) {
        JsonObject exception = result.getMap("exception");
        if (exception != null && exception.getString("message") != null) {
            return exception.getString("message");
        }
        Object error = result.get("error");
        if (error instanceof String text && !text.isBlank()) {
            return text;
        }
        String message = result.getString("message");
        return message != null && !message.isBlank() ? message : "Action failed";
    }

    /**
     * The result the action wrote, or <tt>null</tt>.
     */
    JsonObject result() {
        return result;
    }

    /**
     * Why the action failed, or <tt>null</tt> when it succeeded.
     */
    String error() {
        return error;
    }

    /**
     * The kind of error, such as {@link #NOT_FOUND}, or <tt>null</tt>.
     */
    String reason() {
        return reason;
    }

    /**
     * The exception the action threw, or <tt>null</tt>.
     */
    Throwable failure() {
        return failure;
    }

    /**
     * Whether the error comes from the result itself, which reports a failure of its own (status <tt>error</tt> or
     * <tt>failed</tt>), rather than from an exception, an unknown action or a reported error.
     */
    boolean failedResult() {
        return failedResult;
    }

    /**
     * The error as a reply for the file transport: <tt>{"status":"failed","error":"..."}</tt>, the shape the CLI has
     * read since Camel 4.23, with the result the action wrote before it failed, if any.
     */
    JsonObject errorReply() {
        JsonObject reply = new JsonObject();
        reply.put("status", "failed");
        reply.put("error", error);
        if (reason != null) {
            reply.put("reason", reason);
        }
        if (result != null) {
            reply.put("result", result);
        }
        return reply;
    }

    private static final class Collector implements CliActionOutput {
        private volatile JsonObject result;
        // an action can report several problems, such as one per route it fails to stop
        private final List<String> errors = new ArrayList<>();
        private boolean onlyNotFound = true;

        @Override
        public void write(JsonObject result) {
            this.result = result;
        }

        @Override
        public synchronized void error(String message) {
            errors.add(message);
            onlyNotFound = false;
        }

        @Override
        public synchronized void notFound(String message) {
            errors.add(message);
        }

        synchronized String error() {
            return errors.isEmpty() ? null : String.join("; ", errors);
        }

        synchronized String reason() {
            return !errors.isEmpty() && onlyNotFound ? NOT_FOUND : null;
        }
    }
}
