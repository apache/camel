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

import org.apache.camel.dsl.jbang.core.commands.action.CamelLoadAction;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.CommandLineTestHelper;
import org.apache.camel.util.FileUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.camel.dsl.jbang.run.common.RuntimeUtil.getPid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class LoadTest extends CamelCommandBaseTestSupport {

    private File workingDir;

    @BeforeEach
    @Override
    public void setup() throws Exception {
        super.setup();
        useTestWorkDir();
        useTestHomeDir();
        workingDir = Files.createTempDirectory("camel-load").toFile();
    }

    @AfterEach
    public void end() throws IOException {
        FileUtil.removeDir(workingDir);
    }

    @Test
    public void shouldLoad() throws Exception {
        Run run = new Run(new CamelJBangMain().withPrinter(printer));
        CommandLineTestHelper.parse(run, "src/test/resources/hello.yaml", "--name=myload", "--max-messages=5",
                "--prop=mydir=" + workingDir.getAbsolutePath());

        Runnable r = () -> {
            try {
                // wait for the integration status file to be written to the home dir
                await().atMost(10, TimeUnit.SECONDS)
                        .until(() -> Files.exists(CommandLineHelper.getCamelDir().resolve(getPid() + "-status.json")));
                CamelLoadAction load = new CamelLoadAction(new CamelJBangMain());
                CommandLineTestHelper.parse(load, getPid(), "--source=src/test/resources/load.yaml");
                load.doCall();
            } catch (Exception e) {
                // ignore
            }
        };

        Thread t = new Thread(r);
        t.start();

        int exit = run.doCall();
        Assertions.assertEquals(0, exit);

        Path outPath = workingDir.toPath().resolve("load.txt");
        await("load.txt file is created by the launched routes").untilAsserted(() -> assertThat(outPath).exists());
        String data = Files.readString(outPath);
        Assertions.assertEquals("I was loaded", data);
    }

}
