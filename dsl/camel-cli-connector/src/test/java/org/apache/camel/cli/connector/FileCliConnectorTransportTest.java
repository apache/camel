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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The replies of the file transport. An action with a request id always gets one, so the Camel CLI never has to wait
 * for its timeout: the result, the error, or an ack. An action in the shared action file only gets its result or the
 * error of an exception, as a Camel CLI before 4.24 never reads the other replies.
 * <p>
 * Isolated because it changes {@code user.home}, and every connector in this JVM writes files named after the same pid.
 */
@Isolated
class FileCliConnectorTransportTest {

    private static final String PID = String.valueOf(ProcessHandle.current().pid());

    @TempDir
    Path home;

    private String oldHome;
    private File dir;
    private DefaultCamelContext context;
    private FileCliConnectorTransport transport;
    private final EmptySnapshots snapshots = new EmptySnapshots();
    private final AtomicInteger listings = new AtomicInteger();

    @BeforeEach
    void start() {
        oldHome = System.getProperty("user.home");
        System.setProperty("user.home", home.toString());
        dir = home.resolve(".camel").toFile();

        context = new DefaultCamelContext();
        transport = new FileCliConnectorTransport() {
            @Override
            protected File[] listActionFiles(File dir, String prefix) {
                listings.incrementAndGet();
                return super.listActionFiles(dir, prefix);
            }
        };
        transport.configure(context, FileCliConnectorTransportTest::dispatch, snapshots, () -> {
        });
        transport.start();
        // poll faster than the default second
        transport.updateDelay(50);
    }

    @AfterEach
    void stop() {
        transport.stop();
        context.stop();
        System.setProperty("user.home", oldHome);
    }

    @Test
    void theStatusIsMovedInPlaceAndGoesWithItsTemporaryFile() {
        File status = file(PID + "-status.json");
        // written aside and moved in place, so every read finds a whole status
        assertThat(awaitJson(status).toJson()).isEqualTo("{}");
        assertThat(file(PID + "-status.json.tmp")).doesNotExist();

        transport.stop();
        assertThat(status).doesNotExist();
        assertThat(file(PID + "-status.json.tmp")).doesNotExist();
    }

    @Test
    void anErrorWhileCollectingTheStatusDoesNotEndTheTask() {
        awaitJson(file(PID + "-status.json"));
        // CAMEL-26794: an OutOfMemoryError of a moment; the scheduler would never run a task that threw it again, and
        // the integration would no longer write its status nor answer an action
        snapshots.error = new OutOfMemoryError("Java heap space");
        await().atMost(10, TimeUnit.SECONDS).until(() -> snapshots.error == null);

        writeAction(action("result"), "r1");
        assertThat(awaitJson(file(PID + "-output-r1.json"))).containsEntry("status", "success");
    }

    @Test
    void actionsAreAnsweredBetweenTwoStatusUpdates() {
        // CAMEL-25447: the status every second, as by default, and the actions looked for about every 20 millis
        transport.updateDelay(1000);
        awaitJson(file(PID + "-status.json"));
        int statuses = snapshots.statuses.get();
        long start = System.nanoTime();

        for (int i = 1; i <= 5; i++) {
            writeAction(action("result"), "r" + i);
            assertThat(awaitJson(file(PID + "-output-r" + i + ".json"))).containsEntry("value", "42");
        }

        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        // an action per status update took at least 4 seconds
        assertThat(elapsed).isLessThan(2500);
        // and the status is not collected more often
        assertThat(snapshots.statuses.get() - statuses).isLessThanOrEqualTo((int) (elapsed / 1000) + 2);
    }

    @Test
    void directoryIsOnlyListedBetweenTwoStatusUpdatesWhenAFileIsAdded() {
        // CAMEL-26938: the actions are looked for about 50 times a second, and listing the directory costs far more
        // than looking at its modification time
        transport.updateDelay(1000);
        awaitSharedActionFileRemoved();
        int before = listings.get();

        // listed with every status, and after the status is moved in place, which changes the directory
        await().during(1, TimeUnit.SECONDS).atMost(3, TimeUnit.SECONDS).until(() -> listings.get() - before < 15);

        writeAction(action("result"), "r1");
        assertThat(awaitJson(file(PID + "-output-r1.json"))).containsEntry("value", "42");
    }

    @Test
    void halfWrittenActionIsLookedForAtEveryPoll() throws Exception {
        transport.updateDelay(1000);
        awaitSharedActionFileRemoved();
        File action = writeText("{\"action\":\"res", "r1");
        int before = listings.get();

        // the rest of it is written in place, which leaves the modification time of the directory as it is
        await().atMost(1, TimeUnit.SECONDS).until(() -> listings.get() - before >= 10);
        Files.writeString(action.toPath(), action("result").toJson());

        assertThat(awaitJson(file(PID + "-output-r1.json"))).containsEntry("value", "42");
    }

    @Test
    void resultIsTheReply() {
        writeAction(action("result"), "r1");

        assertThat(awaitJson(file(PID + "-output-r1.json"))).containsEntry("status", "success").containsEntry("value", "42");
        // written aside and moved in place, so the tool never reads half a reply (CAMEL-26911)
        assertThat(file(PID + "-output-r1.json.tmp")).doesNotExist();
    }

    @Test
    void actionWithoutResultIsAcknowledged() {
        writeAction(action("nothing"), "r1");

        assertThat(awaitJson(file(PID + "-output-r1.json")).toJson()).isEqualTo("{\"status\":\"success\"}");
    }

    @Test
    void unknownActionGetsAnError() {
        writeAction(action("does-not-exist"), "r1");

        JsonObject reply = awaitJson(file(PID + "-output-r1.json"));
        assertThat(reply).containsEntry("status", "failed").containsEntry("error", "Unknown action: does-not-exist");
    }

    @Test
    void actionWithoutNameGetsAnError() {
        writeAction(new JsonObject(), "r1");

        assertThat(awaitJson(file(PID + "-output-r1.json"))).containsEntry("error", "Missing action");
    }

    @Test
    void exceptionIsTheError() {
        writeAction(action("boom"), "r1");

        JsonObject reply = awaitJson(file(PID + "-output-r1.json"));
        assertThat(reply).containsEntry("status", "failed").containsEntry("error", "Boom");
    }

    @Test
    void reportedErrorIsTheError() {
        writeAction(action("error"), "r1");

        JsonObject reply = awaitJson(file(PID + "-output-r1.json"));
        assertThat(reply).containsEntry("status", "failed").containsEntry("error", "Broken").doesNotContainKey("reason");
    }

    @Test
    void notFoundHasItsReason() {
        writeAction(action("missing"), "r1");

        JsonObject reply = awaitJson(file(PID + "-output-r1.json"));
        assertThat(reply).containsEntry("status", "failed").containsEntry("error", "No route matching: nope")
                .containsEntry("reason", "not-found");
    }

    @Test
    void exceptionWinsOverAPartialResult() {
        writeAction(action("partial"), "r1");

        JsonObject reply = awaitJson(file(PID + "-output-r1.json"));
        assertThat(reply).containsEntry("status", "failed").containsEntry("error", "Boom");
        JsonObject partial = reply.getMap("result");
        assertThat(partial).containsEntry("value", "42");
    }

    @Test
    void reportedErrorWinsOverAResult() {
        writeAction(action("partial-error"), "r1");

        JsonObject reply = awaitJson(file(PID + "-output-r1.json"));
        assertThat(reply).containsEntry("status", "failed").containsEntry("error", "Half done");
        JsonObject partial = reply.getMap("result");
        assertThat(partial).containsEntry("value", "42");
    }

    @Test
    void resultWithAFailureOfItsOwnIsWrittenAsItIs() {
        writeAction(action("failed-result"), "r1");

        assertThat(awaitJson(file(PID + "-output-r1.json")).toJson())
                .isEqualTo("{\"status\":\"error\",\"message\":\"No such datasource\"}");
    }

    @Test
    void invalidActionGetsAnErrorOnceItIsNotBeingWritten() throws Exception {
        File action = writeText("{\"action\":\"res", "r1");

        // a fresh file may still be written, so the connector waits for it
        await().during(300, TimeUnit.MILLISECONDS).atMost(2, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(action).exists());
        assertThat(file(PID + "-output-r1.json")).doesNotExist();

        assertThat(action.setLastModified(System.currentTimeMillis() - 10_000)).isTrue();
        JsonObject reply = awaitJson(file(PID + "-output-r1.json"));
        assertThat(reply).containsEntry("status", "failed");
        assertThat(reply.getString("error")).startsWith("Invalid action: ");
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertThat(action).doesNotExist());
    }

    @Test
    void halfWrittenActionIsRunWhenComplete() throws Exception {
        File action = writeText("{\"action\":\"res", "r1");
        await().during(300, TimeUnit.MILLISECONDS).atMost(2, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(action).exists());

        Files.writeString(action.toPath(), action("result").toJson());

        assertThat(awaitJson(file(PID + "-output-r1.json"))).containsEntry("value", "42");
    }

    @Test
    void sharedActionFileOnlyGetsResultsAndExceptions() throws Exception {
        File output = file(PID + "-output.json");

        writeAction(action("result"), null);
        assertThat(awaitJson(output)).containsEntry("value", "42");
        Files.writeString(output.toPath(), "");

        writeAction(action("boom"), null);
        assertThat(awaitJson(output)).containsEntry("status", "failed").containsEntry("error", "Boom");
        Files.writeString(output.toPath(), "");

        // as before 4.24: a reported error is not written, but the result is
        writeAction(action("partial-error"), null);
        assertThat(awaitJson(output).toJson()).isEqualTo("{\"status\":\"success\",\"value\":\"42\"}");
        Files.writeString(output.toPath(), "");

        // no reply that a Camel CLI before 4.24 would not read, and the next command could read instead of its own
        for (String name : new String[] { "nothing", "error", "missing", "does-not-exist" }) {
            writeAction(action(name), null);
            await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertThat(file(PID + "-action.json")).doesNotExist());
            assertThat(output).isEmpty();
        }
    }

    @Test
    void emptySharedActionFileIsRemovedWithoutReply() throws Exception {
        File action = file(PID + "-action.json");
        // the connector creates it empty on start
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertThat(action).doesNotExist());

        Files.writeString(action.toPath(), "");
        assertThat(action.setLastModified(System.currentTimeMillis() - 10_000)).isTrue();

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertThat(action).doesNotExist());
        assertThat(file(PID + "-output.json")).isEmpty();
    }

    private static boolean dispatch(JsonObject action, CliActionOutput output) throws Exception {
        String name = action.getString("action");
        if (name == null) {
            return false;
        }
        switch (name) {
            case "result" -> output.write(result());
            case "boom" -> throw new IllegalStateException("Boom");
            case "partial" -> {
                output.write(result());
                throw new IllegalStateException("Boom");
            }
            case "partial-error" -> {
                output.write(result());
                output.error("Half done");
            }
            case "failed-result" -> {
                JsonObject failed = new JsonObject();
                failed.put("status", "error");
                failed.put("message", "No such datasource");
                output.write(failed);
            }
            case "error" -> output.error("Broken");
            case "missing" -> output.notFound("No route matching: nope");
            case "nothing" -> {
                // a fire-and-forget action, such as a route stop
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private static JsonObject result() {
        JsonObject result = new JsonObject();
        result.put("status", "success");
        result.put("value", "42");
        return result;
    }

    private File file(String name) {
        return new File(dir, name);
    }

    private static JsonObject action(String name) {
        JsonObject jo = new JsonObject();
        jo.put("action", name);
        return jo;
    }

    private void writeAction(JsonObject action, String requestId) {
        if (requestId == null) {
            awaitSharedActionFileRemoved();
        }
        writeText(action.toJson(), requestId);
    }

    /**
     * The connector creates an empty shared action file on start, and removes it a little later. Until then it lists
     * the directory at every poll, as a Camel CLI before 4.21 writes its action in that file, in place.
     */
    private void awaitSharedActionFileRemoved() {
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertThat(file(PID + "-action.json")).doesNotExist());
    }

    /**
     * Writes the action atomically, as the Camel CLI does since 4.21 for actions with a request id.
     */
    private File writeText(String text, String requestId) {
        String name = requestId != null ? PID + "-action-" + requestId + ".json" : PID + "-action.json";
        try {
            Path tmp = dir.toPath().resolve(UUID.randomUUID() + ".tmp");
            Files.writeString(tmp, text);
            Files.move(tmp, dir.toPath().resolve(name), StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return file(name);
    }

    private static JsonObject awaitJson(File file) {
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertThat(file).isNotEmpty());
        return await().atMost(10, TimeUnit.SECONDS).until(() -> readJson(file), jo -> jo != null);
    }

    private static JsonObject readJson(File file) {
        try {
            String text = Files.readString(file.toPath());
            return text.isBlank() ? null : (JsonObject) Jsoner.deserialize(text);
        } catch (Exception e) {
            // the file is rewritten while we read it
            return null;
        }
    }

    private static final class EmptySnapshots implements CliSnapshotProducer {
        // thrown once by the next status
        volatile Error error;
        final AtomicInteger statuses = new AtomicInteger();

        @Override
        public JsonObject status() {
            statuses.incrementAndGet();
            Error e = error;
            if (e != null) {
                error = null;
                throw e;
            }
            return new JsonObject();
        }

        @Override
        public JsonObject trace() {
            return null;
        }

        @Override
        public JsonObject debug() {
            return null;
        }

        @Override
        public JsonObject messageHistory() {
            return null;
        }

        @Override
        public JsonObject errors() {
            return null;
        }

        @Override
        public JsonObject receive() {
            return null;
        }

        @Override
        public JsonObject timeline() {
            return null;
        }
    }
}
