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
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * CAMEL-26396: a stop waits for the status write in flight, so a stopped integration leaves no status file behind. The
 * Camel CLI lists a live process with a status file as a running integration: the MCP server, whose scratch contexts
 * ran with the connector, listed itself and took itself for the integration to evaluate in.
 * <p>
 * Isolated because it changes {@code user.home}, and every connector in this JVM writes files named after the same pid.
 */
@Isolated
class FileCliConnectorTransportStopTest {

    private static final String PID = String.valueOf(ProcessHandle.current().pid());

    @TempDir
    Path home;

    private String oldHome;
    private DefaultCamelContext context;

    @BeforeEach
    void useHome() {
        oldHome = System.getProperty("user.home");
        System.setProperty("user.home", home.toString());
        context = new DefaultCamelContext();
    }

    @AfterEach
    void restoreHome() {
        context.stop();
        System.setProperty("user.home", oldHome);
    }

    @Test
    void aStatusWriteInFlightDoesNotOutliveTheStop() throws Exception {
        SlowSnapshots snapshots = new SlowSnapshots();
        FileCliConnectorTransport transport = new FileCliConnectorTransport();
        transport.configure(context, (action, output) -> false, snapshots, () -> {
        });
        transport.start();
        File status = home.resolve(".camel").resolve(PID + "-status.json").toFile();
        // the first poll collects the status, which takes until the test lets it go on
        assertThat(snapshots.collecting.await(10, TimeUnit.SECONDS)).isTrue();

        Thread stopper = new Thread(transport::stop, "test-stop");
        stopper.start();
        // the stop is done, as it was at once before the fix, or it waits for the poll
        await().atMost(10, TimeUnit.SECONDS).until(() -> switch (stopper.getState()) {
            case TERMINATED, WAITING, TIMED_WAITING -> true;
            default -> false;
        });
        snapshots.collected.countDown();

        stopper.join(10_000);
        assertThat(stopper.isAlive()).isFalse();
        // the poll has written the status, before the stop removed the files or after
        snapshots.poll.join(10_000);
        assertThat(snapshots.poll.isAlive()).isFalse();
        assertThat(status).doesNotExist();
    }

    /**
     * Snapshots whose status takes until the test counts down {@link #collected}.
     */
    private static final class SlowSnapshots implements CliSnapshotProducer {

        final CountDownLatch collecting = new CountDownLatch(1);
        final CountDownLatch collected = new CountDownLatch(1);
        volatile Thread poll;

        @Override
        public JsonObject status() throws Exception {
            poll = Thread.currentThread();
            collecting.countDown();
            collected.await(10, TimeUnit.SECONDS);
            JsonObject context = new JsonObject();
            context.put("name", "demo");
            context.put("phase", 4);
            JsonObject root = new JsonObject();
            root.put("context", context);
            return root;
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
        public JsonObject activity() {
            return null;
        }
    }
}
