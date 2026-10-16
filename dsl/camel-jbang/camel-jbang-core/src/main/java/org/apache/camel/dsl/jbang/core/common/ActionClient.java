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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.dsl.jbang.core.common.ProcessFinder.CamelProcess;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/**
 * Sends actions to a running Camel integration and waits for its answer (see camel-cli-connector).
 * <p/>
 * An integration on Camel 4.21 or later takes a file per request (<tt>~/.camel/{pid}-action-{id}.json</tt>, answered in
 * <tt>{pid}-output-{id}.json</tt>), so clients never take each other's answers; an older integration only reads the
 * shared <tt>{pid}-action.json</tt>. An integration on Camel 4.24 or later answers every action, also one without a
 * result, so no answer is a timeout; an older integration does not answer an action without a result, which is then
 * sent but not confirmed.
 */
public final class ActionClient {

    /**
     * How long to wait for an answer by default, in millis.
     */
    public static final long DEFAULT_TIMEOUT = 10_000;

    private static final long POLL_INTERVAL = 100;
    private static final Pattern VERSION = Pattern.compile("^(\\d+)\\.(\\d+)");

    /**
     * How an action went.
     */
    public enum Status {
        /**
         * The integration answered with a result, or confirmed the action.
         */
        OK,
        /**
         * The integration answered that the action failed.
         */
        ERROR,
        /**
         * The integration did not answer in time.
         */
        TIMEOUT,
        /**
         * The integration took the action, but it is too old (before Camel 4.24) to confirm an action without a result.
         */
        UNCONFIRMED
    }

    /**
     * The answer of an integration to an action.
     *
     * @param status how the action went
     * @param reply  the answer of the integration, or <tt>null</tt> when there is none
     * @param error  why the action failed, or did not get an answer; <tt>null</tt> when it went well
     * @param reason the kind of error, such as <tt>not-found</tt>, or <tt>null</tt>
     */
    public record ActionResult(Status status, JsonObject reply, String error, String reason) {

        /**
         * Whether the action went well (also when an older integration took it without confirming it).
         */
        public boolean isOk() {
            return status == Status.OK || status == Status.UNCONFIRMED;
        }

        /**
         * Whether the action failed because what it was asked to work on does not exist.
         */
        public boolean isNotFound() {
            return status == Status.ERROR && "not-found".equals(reason);
        }

        /**
         * The exit code of a command that ends with this result, see {@link CliExitCode}.
         */
        public int exitCode() {
            return switch (status) {
                case OK, UNCONFIRMED -> CliExitCode.OK;
                case ERROR -> isNotFound() ? CliExitCode.NOT_FOUND : CliExitCode.FAILURE;
                case TIMEOUT -> CliExitCode.TIMEOUT;
            };
        }

        /**
         * The reply when the action went well.
         *
         * @throws CliException with the exit code of the result when the action failed or timed out
         */
        public JsonObject orThrow() {
            if (isOk()) {
                return reply;
            }
            throw new CliException(exitCode(), error);
        }
    }

    private ActionClient() {
    }

    /**
     * Runs an action that has a result, such as a route dump, and waits for the result.
     *
     * @param process the integration
     * @param action  the action name
     * @param options sets the options of the action, or <tt>null</tt>
     * @param timeout how long to wait for the result, in millis
     */
    public static ActionResult execute(CamelProcess process, String action, Consumer<JsonObject> options, long timeout) {
        return execute(process.pid(), process.camelVersion(), request(action, options), timeout, true);
    }

    /**
     * Sends an action without a result, such as a route stop, and waits until the integration confirms it.
     *
     * @param process the integration
     * @param action  the action name
     * @param options sets the options of the action, or <tt>null</tt>
     * @param timeout how long to wait for the confirmation, in millis
     */
    public static ActionResult send(CamelProcess process, String action, Consumer<JsonObject> options, long timeout) {
        return execute(process.pid(), process.camelVersion(), request(action, options), timeout, false);
    }

    /**
     * Runs an action that has a result, for an integration known by its pid only.
     *
     * @throws IOException when the action cannot be written
     */
    public static ActionResult execute(long pid, JsonObject request, long timeout) throws IOException {
        return run(pid, versionOf(pid), request, timeout, true);
    }

    /**
     * Sends an action without a result, for an integration known by its pid only, and waits until the integration
     * confirms it.
     *
     * @throws IOException when the action cannot be written
     */
    public static ActionResult send(long pid, JsonObject request, long timeout) throws IOException {
        return run(pid, versionOf(pid), request, timeout, false);
    }

    private static ActionResult execute(
            long pid, String camelVersion, JsonObject request, long timeout, boolean hasResult) {
        try {
            return run(pid, camelVersion, request, timeout, hasResult);
        } catch (IOException e) {
            return new ActionResult(
                    Status.ERROR, null, "Cannot send the action to the integration with pid " + pid + ": " + e.getMessage(),
                    null);
        }
    }

    private static ActionResult run(long pid, String camelVersion, JsonObject request, long timeout, boolean hasResult)
            throws IOException {
        // an unknown version is a recent one (every connector writes its version in the status file)
        boolean confirms = camelVersion == null || isAtLeast(camelVersion, 4, 24);
        Exchange<JsonObject> exchange = exchange(pid, camelVersion, request, timeout, hasResult || confirms,
                text -> Jsoner.deserialize(text, (JsonObject) null));
        if (exchange.reply() != null) {
            return result(exchange.reply());
        }
        if (exchange.taken()) {
            // the integration took the action (it deletes the file), but does not confirm it
            return new ActionResult(Status.UNCONFIRMED, null, null, null);
        }
        String action = request.getString("action");
        return new ActionResult(
                Status.TIMEOUT, null, "No answer from the integration with pid " + pid + " to the action " + action
                                      + " within " + timeout + " millis",
                null);
    }

    /**
     * Runs an action and returns the answer as the integration wrote it, for tools that pass it on as text.
     *
     * @return             the answer, or <tt>null</tt> when there is none in time
     * @throws IOException when the action cannot be written
     */
    public static String executeText(long pid, JsonObject request, long timeout) throws IOException {
        return exchange(pid, versionOf(pid), request, timeout, true, text -> text).reply();
    }

    // the answer to an action, as read, or null; and whether the integration took the action
    private record Exchange<T>(T reply, boolean taken) {
    }

    private static <T> Exchange<T> exchange(
            long pid, String camelVersion, JsonObject request, long timeout, boolean waitForAnswer,
            Function<String, T> reader)
            throws IOException {
        boolean requestFiles = camelVersion == null || isAtLeast(camelVersion, 4, 21);
        Path dir = CommandLineHelper.getCamelDir();
        Path actionFile;
        Path outputFile;
        if (requestFiles) {
            String requestId = UUID.randomUUID().toString();
            actionFile = dir.resolve(pid + "-action-" + requestId + ".json");
            outputFile = dir.resolve(pid + "-output-" + requestId + ".json");
        } else {
            actionFile = dir.resolve(pid + "-action.json");
            outputFile = dir.resolve(pid + "-output.json");
            // an answer left behind by an earlier command
            Files.deleteIfExists(outputFile);
        }
        Path temporary = dir.resolve(pid + "-" + UUID.randomUUID() + ".tmp");
        try {
            Files.writeString(temporary, request.toJson());
            // the integration must never read half an action
            Files.move(temporary, actionFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);

            StopWatch watch = new StopWatch();
            while (watch.taken() < timeout) {
                try {
                    Thread.sleep(Math.min(POLL_INTERVAL, Math.max(1, timeout - watch.taken())));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                if (waitForAnswer) {
                    T reply = readReply(outputFile, reader);
                    if (reply != null) {
                        return new Exchange<>(reply, true);
                    }
                } else if (!Files.exists(actionFile)) {
                    return new Exchange<>(null, true);
                }
            }
            return new Exchange<>(null, false);
        } finally {
            // removing an action the integration has not taken yet cancels it
            Files.deleteIfExists(temporary);
            Files.deleteIfExists(actionFile);
            Files.deleteIfExists(outputFile);
        }
    }

    private static <T> T readReply(Path outputFile, Function<String, T> reader) {
        try {
            if (Files.exists(outputFile)) {
                String text = Files.readString(outputFile);
                if (!text.isBlank()) {
                    // null when the answer is still being written
                    return reader.apply(text);
                }
            }
        } catch (IOException e) {
            // the integration writes the answer in place: try again
        }
        return null;
    }

    /**
     * The result of an answer: an error when its status is <tt>failed</tt> or <tt>error</tt>, the same as the connector
     * does for its WebSocket transport.
     */
    public static ActionResult result(JsonObject reply) {
        String status = reply.getString("status");
        if ("failed".equals(status) || "error".equals(status)) {
            return new ActionResult(Status.ERROR, reply, messageOf(reply), reply.getString("reason"));
        }
        return new ActionResult(Status.OK, reply, null, null);
    }

    private static String messageOf(JsonObject reply) {
        if (reply.get("exception") instanceof JsonObject exception && exception.getString("message") != null) {
            return exception.getString("message");
        }
        if (reply.get("error") instanceof String error && !error.isBlank()) {
            return error;
        }
        String message = reply.getString("message");
        return message != null && !message.isBlank() ? message : "Action failed";
    }

    private static JsonObject request(String action, Consumer<JsonObject> options) {
        JsonObject request = new JsonObject();
        request.put("action", action);
        if (options != null) {
            options.accept(request);
        }
        return request;
    }

    private static String versionOf(long pid) {
        try {
            Path f = CommandLineHelper.getCamelDir().resolve(pid + "-status.json");
            JsonObject status = Files.exists(f) ? Jsoner.deserialize(Files.readString(f), (JsonObject) null) : null;
            if (status != null && status.get("context") instanceof JsonObject context) {
                return context.getString("version");
            }
        } catch (Exception e) {
            // unknown
        }
        return null;
    }

    /**
     * Whether a Camel version such as <tt>4.24.0-SNAPSHOT</tt> is the given major.minor or later.
     */
    static boolean isAtLeast(String version, int major, int minor) {
        Matcher m = VERSION.matcher(version);
        if (!m.find()) {
            // not a version we know how to read: assume a recent one
            return true;
        }
        int actualMajor = Integer.parseInt(m.group(1));
        int actualMinor = Integer.parseInt(m.group(2));
        return actualMajor > major || actualMajor == major && actualMinor >= minor;
    }
}
