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
package org.apache.camel.dsl.jbang.core.commands;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.dsl.jbang.core.commands.action.CamelLoadAction;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.CommandLineTestHelper;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.HomeHelper;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.camel.dsl.jbang.run.common.RuntimeUtil.getPid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class LoadTest extends CamelCommandBaseTestSupport {

    // the phase of a started integration in the status file
    private static final int PHASE_STARTED = 5;

    private File workingDir;

    @BeforeEach
    @Override
    public void setup() throws Exception {
        super.setup();
        useTestWorkDir();
        // the routes run in this JVM, and their CLI connector reads the actions in the user home
        useHomeDir(Path.of(HomeHelper.resolveHomeDir()));
        workingDir = Files.createTempDirectory("camel-load").toFile();
    }

    @AfterEach
    public void end() throws IOException {
        FileUtil.removeDir(workingDir);
    }

    private static boolean integrationStarted() throws Exception {
        Path status = CommandLineHelper.getCamelDir().resolve(getPid() + "-status.json");
        if (!Files.exists(status)) {
            return false;
        }
        JsonObject root = (JsonObject) Jsoner.deserialize(Files.readString(status));
        JsonObject context = root != null ? (JsonObject) root.get("context") : null;
        return context != null && context.getInteger("phase") == PHASE_STARTED;
    }

    @Test
    public void shouldLoad() throws Exception {
        Run run = new CamelJBangMain().withPrinter(printer).command(new Run());
        CommandLineTestHelper.parse(run, "src/test/resources/hello.yaml", "--name=myload", "--max-messages=5",
                "--prop=mydir=" + workingDir.getAbsolutePath());

        AtomicReference<Exception> loadFailure = new AtomicReference<>();
        Runnable r = () -> {
            try {
                // wait until the integration has started: the status file is in the home dir, and it is empty or
                // incomplete while the integration writes it
                await().atMost(10, TimeUnit.SECONDS).ignoreExceptions().until(LoadTest::integrationStarted);
                CamelLoadAction load = new CamelJBangMain().command(new CamelLoadAction());
                CommandLineTestHelper.parse(load, getPid(), "--source=src/test/resources/load.yaml");
                load.doCall();
            } catch (Exception e) {
                loadFailure.set(e);
            }
        };

        Thread t = new Thread(r);
        t.start();

        int exit = run.doCall();
        t.join();
        assertThat(loadFailure.get()).as("the load action is sent").isNull();
        Assertions.assertEquals(0, exit);

        Path outPath = workingDir.toPath().resolve("load.txt");
        await("load.txt file is created by the launched routes").untilAsserted(() -> assertThat(outPath).exists());
        String data = Files.readString(outPath);
        Assertions.assertEquals("I was loaded", data);
    }

}
