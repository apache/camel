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
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StringHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class TransformTest {

    private File workingDir;

    @BeforeEach
    public void setup() throws IOException {
        Path base = Paths.get("target");
        workingDir = Files.createTempDirectory(base, "camel-transform").toFile();
    }

    @AfterEach
    public void end() throws IOException {
        // force removing, since deleteOnExit is not removing.
        FileUtil.removeDir(workingDir);
    }

    @Test
    public void shouldTransformToYaml() throws Exception {
        String name = workingDir + "/transform.yaml";
        File out = new File(name);

        String[] args = new String[] { "--output=" + out.getPath() };
        TransformRoute command = createCommand(new String[] { "src/test/resources/transform.xml" }, args);
        int exit = command.doCall();
        Assertions.assertEquals(0, exit);

        Assertions.assertTrue(out.exists());
        String data = IOHelper.loadText(new FileInputStream(out));
        String expected = IOHelper.loadText(new FileInputStream("src/test/resources/transform-out.yaml"));
        expected = "- route:" + StringHelper.after(expected, "- route:"); // skip license header
        Assertions.assertEquals(expected, data);
    }

    private TransformRoute createCommand(String[] files, String... args) {
        TransformRoute command = new TransformRoute(new CamelJBangMain());

        CommandLine.populateCommand(command, "--format=yaml", "--uri-as-parameters");
        if (args != null) {
            CommandLine.populateCommand(command, args);
        }
        command.files = Arrays.asList(files);
        return command;
    }

}
