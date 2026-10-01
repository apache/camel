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
 */
public class FileCliConnectorTransport extends ServiceSupport implements CliConnectorTransport {

    // log as the connector, which is where users know to find these messages
    private static final Logger LOG = LoggerFactory.getLogger(LocalCliConnector.class);

    private CamelContext camelContext;
    private CliActionDispatcher dispatcher;
    private CliSnapshotProducer snapshots;
    private Runnable shutdown;
    private ScheduledExecutorService executor;
    private ScheduledFuture<?> scheduledFuture;
    private int delay = 1000;
    private long counter;
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
    private File activityFile;

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
            activityFile = createLockFile(lockFile.getName() + "-activity.json");
            scheduledFuture = executor.scheduleWithFixedDelay(this::task, 0, delay, TimeUnit.MILLISECONDS);
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
        boolean done = scheduledFuture == null || scheduledFuture.isDone();
        if (done) {
            this.delay = delay;
            scheduledFuture = executor.scheduleWithFixedDelay(this::task, 0, delay, TimeUnit.MILLISECONDS);
        }
    }

    protected void task() {
        if (!lockFile.exists() && terminating.compareAndSet(false, true)) {
            // if the lock file is deleted then trigger termination
            shutdown.run();
            return;
        }
        if (!statusFile.exists()) {
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

    protected void actionTask() {
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
            processAction(af, of);
        }
    }

    private void processAction(File af, File of) {
        String action = null;
        try {
            JsonObject root = loadAction(af);
            if (root == null || root.isEmpty()) {
                return;
            }
            action = root.getString("action");
            dispatcher.dispatch(root, result -> {
                LOG.trace("Updating output file: {}", of);
                IOHelper.writeText(result.toJson(), of);
            });
        } catch (Exception e) {
            LOG.warn("Error executing action: {} due to: {}. This exception is ignored.", action != null ? action : af,
                    e.getMessage(),
                    e);
        } finally {
            FileUtil.deleteFile(af);
        }
    }

    private static JsonObject loadAction(File file) {
        try {
            if (file != null && file.exists()) {
                try (FileInputStream fis = new FileInputStream(file)) {
                    String text = IOHelper.loadText(fis);
                    if (!text.isEmpty()) {
                        return (JsonObject) Jsoner.deserialize(text);
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    protected void statusTask() {
        try {
            // even during termination then collect status as we want to see status changes during stopping
            JsonObject root = snapshots.status();
            LOG.trace("Updating status file: {}", statusFile);
            IOHelper.writeText(root.toJson(), statusFile);
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
                IOHelper.writeText(data, debugFile);
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
                IOHelper.writeText(data, messageHistoryFile);
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
                IOHelper.writeText(data, errorFile);
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
            JsonObject json = snapshots.activity();
            if (json != null) {
                LOG.trace("Updating activity file: {}", activityFile);
                String data = json.toJson() + System.lineSeparator();
                IOHelper.writeText(data, activityFile);
            }
        } catch (Exception e) {
            // ignore
            LOG.trace("Error updating activity file: {} due to: {}. This exception is ignored.",
                    activityFile, e.getMessage(), e);
        }
    }

    @Override
    protected void doStop() throws Exception {
        // stop polling first: a poll seeing the lock file deleted below would trigger a shutdown
        terminating.set(true);
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
        }
        // cleanup
        if (lockFile != null) {
            FileUtil.deleteFile(lockFile);
        }
        if (statusFile != null) {
            FileUtil.deleteFile(statusFile);
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
        }
        if (errorFile != null) {
            FileUtil.deleteFile(errorFile);
        }
        if (debugFile != null) {
            FileUtil.deleteFile(debugFile);
        }
        if (receiveFile != null) {
            FileUtil.deleteFile(receiveFile);
        }
        if (activityFile != null) {
            FileUtil.deleteFile(activityFile);
        }
        if (executor != null) {
            camelContext.getExecutorServiceManager().shutdown(executor);
            executor = null;
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
