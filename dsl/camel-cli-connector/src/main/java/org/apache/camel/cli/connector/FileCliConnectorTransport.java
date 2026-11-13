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
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.CamelContext;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.HomeHelper;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.concurrent.ThreadHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Transport used by the Camel CLI: polls <tt>~/.camel/{pid}-action*.json</tt> files for actions, writes their results
 * to <tt>{pid}-output*.json</tt>, and the snapshots to <tt>{pid}-status.json</tt>, <tt>{pid}-trace.json</tt> and so on.
 * Deleting the <tt>{pid}</tt> lock file shuts down the integration.
 * <p/>
 * The snapshots are written every second (every 100 millis in debug mode), and the action files are looked for about
 * every 100 millis in between, on the same thread, so an action does not wait up to a second for its answer. The status
 * is also written as soon as Camel changes phase, such as when it has started, and after actions are answered (at most
 * every 200 millis), so a <tt>camel run --background</tt> is told that Camel has started without waiting for the next
 * status, and a <tt>camel get</tt> right after a <tt>camel cmd</tt> shows what the action did.
 * <p/>
 * An action with a request id (<tt>{pid}-action-{requestId}.json</tt>) always gets a reply in
 * <tt>{pid}-output-{requestId}.json</tt>: <tt>{"status":"failed","error":"..."}</tt> when it is unknown, throws or
 * reports an error (with <tt>"reason":"not-found"</tt> when what it was asked to work on does not exist, and the result
 * it wrote before it failed under <tt>"result"</tt>), its result, or <tt>{"status":"success"}</tt> when it has no
 * result. A result that reports a failure of its own is written as it is. An action in the shared
 * <tt>{pid}-action.json</tt> only gets its result, or the error when it threw an exception, as a Camel CLI before 4.24
 * never reads the other replies, and the next command could read them instead of its own result.
 */
public class FileCliConnectorTransport extends ServiceSupport implements CliConnectorTransport {

    // log as the connector, which is where users know to find these messages
    private static final Logger LOG = LoggerFactory.getLogger(LocalCliConnector.class);

    private CamelContext camelContext;
    private CliActionDispatcher dispatcher;
    private CliSnapshotProducer snapshots;
    private Runnable shutdown;
    private ScheduledExecutorService executor;
    private volatile Thread pollThread;
    private ScheduledFuture<?> scheduledFuture;
    private ScheduledFuture<?> actionFuture;
    private int delay = 1000;
    private long counter;
    private final Map<File, PendingAction> pending = new HashMap<>();
    // the phase of Camel when the status was last written, when it was written, and whether an action was answered since
    private int statusPhase = -1;
    private long statusWritten;
    private boolean answered;

    // an action file this young that cannot be read may still be written: the Camel CLI before 4.24 writes it in place
    private static final long UNREADABLE_GRACE_MS = 2000;
    // how long a stop waits for the poll in flight before it removes the files
    private static final long STOP_POLL_TIMEOUT_MS = 5000;
    // how often the action files are looked for between two snapshots
    private static final int ACTION_POLL_MS = 100;
    // how often at most the status is written for the actions answered between two snapshots, as collecting it costs
    private static final long ANSWERED_STATUS_MS = 200;

    private record PendingAction(File output, boolean replyAlways, CompletableFuture<CliActionOutcome> outcome) {
    }

    private final AtomicBoolean terminating = new AtomicBoolean();
    private File lockFile;
    private File statusFile;
    private File actionFile;
    private File outputFile;
    private File traceFile;
    private long traceFilePos;   // keep track of trace offset
    private File messageHistoryFile;
    private File debugFile;
    private File errorFile;
    private File receiveFile;
    private long receiveFilePos; // keep track of receive offset
    private File timelineFile;

    @Override
    public void configure(
            CamelContext camelContext, CliActionDispatcher dispatcher, CliSnapshotProducer snapshots, Runnable shutdown) {
        this.camelContext = camelContext;
        this.dispatcher = dispatcher;
        this.snapshots = snapshots;
        this.shutdown = shutdown;
    }

    @Override
    protected void doStart() throws Exception {
        terminating.set(false);

        // create thread from JDK so it is not managed by Camel because we want the pool to be independent when
        // camel is being stopped which otherwise can lead to stopping the thread pool while the task is running.
        // a daemon thread: the connector starts before Camel, so when the application fails to start (Spring Boot
        // APPLICATION FAILED TO START) Camel never stops it, and it must not keep the JVM alive (CAMEL-25230)
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            String threadName = ThreadHelper.resolveThreadName(null, "LocalCliConnector");
            Thread thread = new Thread(r, threadName);
            thread.setDaemon(true);
            pollThread = thread;
            return thread;
        });

        // make it go faster in debug mode
        if (camelContext.isDebugging()) {
            delay = 100;
        }

        lockFile = createLockFile(getPid());
        if (lockFile != null) {
            statusFile = createLockFile(lockFile.getName() + "-status.json");
            actionFile = createLockFile(lockFile.getName() + "-action.json");
            outputFile = createLockFile(lockFile.getName() + "-output.json");
            traceFile = createLockFile(lockFile.getName() + "-trace.json");
            messageHistoryFile = createLockFile(lockFile.getName() + "-history.json");
            errorFile = createLockFile(lockFile.getName() + "-error.json");
            debugFile = createLockFile(lockFile.getName() + "-debug.json");
            receiveFile = createLockFile(lockFile.getName() + "-receive.json");
            timelineFile = createLockFile(lockFile.getName() + "-timeline.json");
            schedule();
            LOG.info("Camel CLI connector enabled");
        } else {
            LOG.warn("Cannot create PID file: {}. This integration cannot be managed by Camel CLI connector.", getPid());
        }
    }

    @Override
    public void updateDelay(int delay) {
        if (this.delay == delay) {
            return;
        }
        if (scheduledFuture != null) {
            try {
                scheduledFuture.cancel(true);
            } catch (Exception e) {
                // ignore
            }
        }
        if (actionFuture != null) {
            actionFuture.cancel(false);
            actionFuture = null;
        }
        boolean done = scheduledFuture == null || scheduledFuture.isDone();
        if (done) {
            this.delay = delay;
            if (lockFile != null) {
                schedule();
            }
        }
    }

    /**
     * Schedules the poll, and the action poll between two polls when the poll is slower than it (CAMEL-25447). Both on
     * the single thread of the executor, so the actions still run one at a time and in order.
     */
    private void schedule() {
        scheduledFuture = executor.scheduleWithFixedDelay(() -> ConnectorTasks.safely(this::task), 0, delay,
                TimeUnit.MILLISECONDS);
        if (delay > ACTION_POLL_MS) {
            actionFuture = executor.scheduleWithFixedDelay(() -> ConnectorTasks.safely(this::actionPollTask),
                    ACTION_POLL_MS, ACTION_POLL_MS, TimeUnit.MILLISECONDS);
        }
    }

    protected void task() {
        if (!polling()) {
            return;
        }

        actionTask();
        statusTask();
        // only run this every 2nd time as gathering this data has more overhead
        // and are only needed when doing tracing/debugging/receive
        if (++counter % 2 == 0) {
            traceTask();
        }
    }

    /**
     * Looks for actions between two snapshots, so an action is answered in about 100 millis instead of up to a second.
     * Writes the status when Camel has changed phase since it was last written, and when an action was answered (at
     * most every 200 millis).
     */
    protected void actionPollTask() {
        if (polling()) {
            actionTask();
            if (statusPhase != camelContext.getCamelContextExtension().getStatusPhase()
                    || (answered && TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - statusWritten) >= ANSWERED_STATUS_MS)) {
                statusTask();
            }
        }
    }

    /**
     * Whether to poll: not when the status file is gone, nor once the lock file is deleted, which shuts down the
     * integration.
     */
    private boolean polling() {
        if (!lockFile.exists() && terminating.compareAndSet(false, true)) {
            // if the lock file is deleted then trigger termination
            shutdown.run();
            return false;
        }
        return statusFile.exists();
    }

    protected synchronized void actionTask() {
        pending.entrySet().removeIf(entry -> {
            File request = entry.getKey();
            PendingAction action = entry.getValue();
            if (!request.exists()) {
                action.outcome().cancel(true);
                return true;
            }
            if (action.outcome().isDone()) {
                finishAction(request, action);
                return true;
            }
            return false;
        });
        // scan for all action files: {pid}-action.json (legacy) and {pid}-action-{requestId}.json (multi-client)
        File dir = lockFile.getParentFile();
        String prefix = lockFile.getName() + "-action";
        File[] actionFiles = dir.listFiles((d, name) -> name.startsWith(prefix) && name.endsWith(".json"));
        if (actionFiles == null || actionFiles.length == 0) {
            return;
        }
        for (File af : actionFiles) {
            String suffix = af.getName().substring(prefix.length());
            // suffix is either ".json" (legacy) or "-{requestId}.json" (multi-client)
            String requestId = suffix.startsWith("-")
                    ? suffix.substring(1, suffix.length() - 5)  // strip leading "-" and trailing ".json"
                    : null;
            File of = requestId != null
                    ? new File(dir, lockFile.getName() + "-output-" + requestId + ".json")
                    : this.outputFile;
            if (!pending.containsKey(af)) {
                processAction(af, of, requestId != null);
            }
        }
    }

    private void processAction(File af, File of, boolean replyAlways) {
        String text = readAction(af);
        if (text == null) {
            // deleted meanwhile
            return;
        }
        JsonObject root = null;
        String invalid = null;
        if (!text.isBlank()) {
            try {
                Object parsed = Jsoner.deserialize(text);
                if (parsed instanceof JsonObject jo) {
                    root = jo;
                } else {
                    invalid = "not a JSON object";
                }
            } catch (Exception e) {
                invalid = e.getMessage();
            }
        }
        if (root == null) {
            if (System.currentTimeMillis() - af.lastModified() < UNREADABLE_GRACE_MS) {
                // try again at the next poll, the file may still be written
                return;
            }
            if (invalid != null && replyAlways) {
                writeReply(af, of, CliActionOutcome.failed("Invalid action: " + invalid).errorReply());
            }
            FileUtil.deleteFile(af);
            return;
        }
        PendingAction action = new PendingAction(of, replyAlways, CliActionOutcome.dispatch(dispatcher, root));
        if (action.outcome().isDone()) {
            finishAction(af, action);
        } else {
            // Keep the request file until completion; its removal means the client abandoned the request.
            pending.put(af, action);
        }
    }

    private void finishAction(File request, PendingAction action) {
        try {
            CliActionOutcome outcome = action.outcome().join();
            if (outcome.failure() != null) {
                LOG.warn("Error executing action: {} due to: {}", request, outcome.error(), outcome.failure());
            }
            JsonObject reply = reply(outcome, action.replyAlways());
            if (reply != null) {
                writeReply(request, action.output(), reply);
            }
        } catch (Exception e) {
            LOG.warn("Error writing action result: {} due to: {}", request, e.getMessage(), e);
        } finally {
            FileUtil.deleteFile(request);
            // the status shows what the action did
            answered = true;
        }
    }

    /**
     * The reply to an action: the error, its result, or an ack. The shared output file only gets the result, or the
     * error of an exception (as before 4.24), so the next command does not read a reply meant for another command.
     */
    private static JsonObject reply(CliActionOutcome outcome, boolean replyAlways) {
        if (outcome.error() != null && !outcome.failedResult()) {
            // an exception, an unknown action or a reported error wins over a (partial) result
            if (replyAlways || outcome.failure() != null) {
                return outcome.errorReply();
            }
            return outcome.result();
        }
        if (outcome.result() != null) {
            // also a result that reports a failure of its own: the CLI reads it as it is
            return outcome.result();
        }
        if (replyAlways) {
            JsonObject ack = new JsonObject();
            ack.put("status", "success");
            return ack;
        }
        return null;
    }

    private static void writeReply(File request, File output, JsonObject reply) {
        // a deleted request means the tool has given up on it
        if (request.exists()) {
            try {
                // written aside and moved in place: the tool reads the answer as soon as it is there (CAMEL-26911)
                writeAtomically(reply.toJson(), output);
            } catch (Exception e) {
                LOG.warn("Error writing action result: {} due to: {}", request, e.getMessage(), e);
            }
        }
    }

    /**
     * Writes the text to a file aside and moves it in place, so a reader never finds half of it, as the status file.
     */
    private static void writeAtomically(String text, File file) throws IOException {
        File temporary = new File(file.getPath() + ".tmp");
        IOHelper.writeText(text, temporary);
        try {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String readAction(File file) {
        try {
            if (file != null && file.exists()) {
                try (FileInputStream fis = new FileInputStream(file)) {
                    return IOHelper.loadText(fis);
                }
            }
        } catch (Exception e) {
            // deleted meanwhile
        }
        return null;
    }

    protected void statusTask() {
        statusPhase = camelContext.getCamelContextExtension().getStatusPhase();
        statusWritten = System.nanoTime();
        answered = false;
        try {
            // even during termination then collect status as we want to see status changes during stopping
            JsonObject root = snapshots.status();
            LOG.trace("Updating status file: {}", statusFile);
            // written aside and moved in place: a reader must never find half a status
            File temporary = new File(statusFile.getPath() + ".tmp");
            IOHelper.writeText(root.toJson(), temporary);
            Files.move(temporary.toPath(), statusFile.toPath(), StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            // ignore
            LOG.trace("Error updating status file: {} due to: {}. This exception is ignored.",
                    statusFile, e.getMessage(), e);
        }
    }

    protected void traceTask() {
        try {
            JsonObject json = snapshots.trace();
            if (json != null) {
                JsonArray arr = json.getCollection("traces");
                // filter based on last uid
                if (traceFilePos > 0) {
                    arr.removeIf(r -> {
                        JsonObject jo = (JsonObject) r;
                        return jo.getLong("uid") <= traceFilePos;
                    });
                }
                if (arr != null && !arr.isEmpty()) {
                    // store traces in a special file
                    LOG.trace("Updating trace file: {}", traceFile);
                    String data = json.toJson() + System.lineSeparator();
                    IOHelper.appendText(data, traceFile);
                    json = arr.getMap(arr.size() - 1);
                    traceFilePos = json.getLong("uid");
                }
            }
        } catch (Exception e) {
            // ignore
            LOG.trace("Error updating trace file: {} due to: {}. This exception is ignored.",
                    traceFile, e.getMessage(), e);
        }
        try {
            JsonObject json = snapshots.debug();
            if (json != null) {
                // store debugs in a special file
                LOG.trace("Updating debug file: {}", debugFile);
                String data = json.toJson() + System.lineSeparator();
                writeAtomically(data, debugFile);
            }
        } catch (Exception e) {
            // ignore
            LOG.trace("Error updating debug file: {} due to: {}. This exception is ignored.",
                    debugFile, e.getMessage(), e);
        }
        try {
            JsonObject json = snapshots.messageHistory();
            if (json != null) {
                // store replays in a special file
                LOG.trace("Updating message-history file: {}", messageHistoryFile);
                String data = json.toJson() + System.lineSeparator();
                writeAtomically(data, messageHistoryFile);
            }
        } catch (Exception e) {
            // ignore
            LOG.trace("Error updating message-history file: {} due to: {}. This exception is ignored.",
                    messageHistoryFile, e.getMessage(), e);
        }
        try {
            JsonObject json = snapshots.errors();
            if (json != null && !json.isEmpty()) {
                LOG.trace("Updating error file: {}", errorFile);
                String data = json.toJson() + System.lineSeparator();
                writeAtomically(data, errorFile);
            }
        } catch (Exception e) {
            // ignore
            LOG.trace("Error updating error file: {} due to: {}. This exception is ignored.",
                    errorFile, e.getMessage(), e);
        }
        try {
            JsonObject json = snapshots.receive();
            if (json != null) {
                JsonArray arr = json.getCollection("messages");
                // filter based on last uid
                if (receiveFilePos > 0) {
                    arr.removeIf(r -> {
                        JsonObject jo = (JsonObject) r;
                        return jo.getLong("uid") <= receiveFilePos;
                    });
                }
                if (arr != null && !arr.isEmpty()) {
                    // store messages in a special file
                    LOG.trace("Updating receive file: {}", receiveFile);
                    String data = json.toJson() + System.lineSeparator();
                    IOHelper.appendText(data, receiveFile);
                    json = arr.getMap(arr.size() - 1);
                    receiveFilePos = json.getLong("uid");
                }
            }
        } catch (Exception e) {
            // ignore
            LOG.trace("Error updating receive file: {} due to: {}. This exception is ignored.",
                    receiveFile, e.getMessage(), e);
        }
        try {
            JsonObject json = snapshots.timeline();
            if (json != null) {
                LOG.trace("Updating timeline file: {}", timelineFile);
                String data = json.toJson() + System.lineSeparator();
                writeAtomically(data, timelineFile);
            }
        } catch (Exception e) {
            // ignore
            LOG.trace("Error updating timeline file: {} due to: {}. This exception is ignored.",
                    timelineFile, e.getMessage(), e);
        }
    }

    @Override
    protected void doStop() throws Exception {
        // stop polling first: a poll seeing the lock file deleted below would trigger a shutdown
        terminating.set(true);
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
        }
        if (actionFuture != null) {
            actionFuture.cancel(false);
        }
        awaitPoll();
        synchronized (this) {
            pending.forEach((request, action) -> {
                action.outcome().cancel(true);
                FileUtil.deleteFile(request);
            });
            pending.clear();
        }
        // cleanup
        if (lockFile != null) {
            FileUtil.deleteFile(lockFile);
        }
        if (statusFile != null) {
            FileUtil.deleteFile(statusFile);
            FileUtil.deleteFile(new File(statusFile.getPath() + ".tmp"));
        }
        if (actionFile != null) {
            FileUtil.deleteFile(actionFile);
        }
        if (outputFile != null) {
            FileUtil.deleteFile(outputFile);
        }
        if (traceFile != null) {
            FileUtil.deleteFile(traceFile);
        }
        if (messageHistoryFile != null) {
            FileUtil.deleteFile(messageHistoryFile);
            FileUtil.deleteFile(new File(messageHistoryFile.getPath() + ".tmp"));
        }
        if (errorFile != null) {
            FileUtil.deleteFile(errorFile);
            FileUtil.deleteFile(new File(errorFile.getPath() + ".tmp"));
        }
        if (debugFile != null) {
            FileUtil.deleteFile(debugFile);
            FileUtil.deleteFile(new File(debugFile.getPath() + ".tmp"));
        }
        if (receiveFile != null) {
            FileUtil.deleteFile(receiveFile);
        }
        if (timelineFile != null) {
            FileUtil.deleteFile(timelineFile);
            FileUtil.deleteFile(new File(timelineFile.getPath() + ".tmp"));
        }
        if (executor != null) {
            camelContext.getExecutorServiceManager().shutdown(executor);
            executor = null;
        }
    }

    /**
     * Waits a little for the poll in flight: a status written after the files are removed would stay behind, and the
     * Camel CLI would list this process as a running integration after its Camel has stopped (CAMEL-26396). Not when
     * the poll itself stops the transport, which would wait for itself.
     */
    private void awaitPoll() {
        if (executor == null) {
            return;
        }
        // the poll is cancelled, so the thread ends once the poll in flight is done
        executor.shutdown();
        if (Thread.currentThread() == pollThread) {
            return;
        }
        try {
            if (!executor.awaitTermination(STOP_POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                LOG.debug("The poll of the Camel CLI connector has not finished within {} millis", STOP_POLL_TIMEOUT_MS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String getPid() {
        return String.valueOf(ProcessHandle.current().pid());
    }

    private static File createLockFile(String name) {
        File answer = null;
        if (name != null) {
            File dir = new File(HomeHelper.resolveHomeDir(), ".camel");
            try {
                dir.mkdirs();
                answer = new File(dir, name);
                if (!answer.exists()) {
                    answer.createNewFile();
                }
                answer.deleteOnExit();
            } catch (Exception e) {
                answer = null;
            }
        }
        return answer;
    }
}
