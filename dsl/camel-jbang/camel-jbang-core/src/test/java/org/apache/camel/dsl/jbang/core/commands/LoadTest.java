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

import static org.apache.camel.dsl.jbang.core.common.RuntimeUtil.getPid;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.camel.dsl.jbang.core.commands.action.CamelLoadAction;
import org.apache.camel.dsl.jbang.core.common.PathUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class LoadTest {

    private Path workingDir;

    @BeforeEach
    public void setup() throws IOException {
        Path base = Paths.get("target");
        workingDir = Files.createTempDirectory(base, "camel-load");
    }

    @AfterEach
    public void end() throws IOException {
        // force removing, since deleteOnExit is not removing.
        PathUtils.deleteDirectory(workingDir);
    }

    @Test
    public void shouldLoad() throws Exception {
        Run run = new Run(new CamelJBangMain());
        CommandLine.populateCommand(
                run,
                "src/test/resources/hello.yaml",
                "--name=myload",
                "--max-messages=5",
                "--prop=mydir=" + workingDir);

        Runnable r = () -> {
            try {
                Thread.sleep(2000);
                CamelLoadAction load = new CamelLoadAction(new CamelJBangMain());
                CommandLine.populateCommand(load, getPid(), "--source=src/test/resources/load.yaml");
                int exit = load.doCall();
                Assertions.assertEquals(0, exit);
            } catch (Exception e) {
                // ignore
            }
        };

        Thread t = new Thread(r);
        t.start();

        int exit = run.doCall();
        Assertions.assertEquals(0, exit);

        Path outPath = workingDir.resolve("load.txt");
        Assertions.assertTrue(Files.exists(outPath));
        String data = Files.readString(outPath);
        Assertions.assertEquals("I was loaded", data);
    }
}
