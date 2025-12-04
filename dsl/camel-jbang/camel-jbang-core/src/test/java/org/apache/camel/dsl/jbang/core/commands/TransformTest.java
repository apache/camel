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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import org.apache.camel.dsl.jbang.core.common.PathUtils;
import org.apache.camel.util.IOHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class TransformTest {

    private Path workingDir;

    @BeforeEach
    public void setup() throws IOException {
        Path base = Paths.get("target");
        workingDir = Files.createTempDirectory(base, "camel-transform");
    }

    @AfterEach
    public void end() throws IOException {
        // force removing, since deleteOnExit is not removing.
        PathUtils.deleteDirectory(workingDir);
    }

    @Test
    public void shouldTransformToYaml() throws Exception {
        Path outPath = workingDir.resolve("transform.yaml");

        String[] args = new String[] {"--output=" + outPath.toString()};
        TransformRoute command = createCommand(new String[] {"src/test/resources/transform.xml"}, args);
        int exit = command.doCall();
        Assertions.assertEquals(0, exit);

        Assertions.assertTrue(Files.exists(outPath));
        String data = Files.readString(outPath);
        String expected = IOHelper.stripLineComments(Paths.get("src/test/resources/transform-out.yaml"), "#", true);
        Assertions.assertEquals(expected, data);
    }

    @Test
    public void shouldTransformBlueprintToYaml() throws Exception {
        Path outPath = workingDir.resolve("blueprint.yaml");

        String[] args = new String[] {"--output=" + outPath.toString()};
        TransformRoute command = createCommand(new String[] {"src/test/resources/blueprint.xml"}, args);
        int exit = command.doCall();
        Assertions.assertEquals(0, exit);

        Assertions.assertTrue(Files.exists(outPath));
        String data = Files.readString(outPath);
        String expected = IOHelper.stripLineComments(Paths.get("src/test/resources/blueprint-out.yaml"), "#", true);
        assertThat(data).isEqualToIgnoringNewLines(expected);
    }

    private TransformRoute createCommand(String[] files, String... args) {
        TransformRoute command = new TransformRoute(new CamelJBangMain());

        CommandLine.populateCommand(command, "--format=yaml");
        if (args != null) {
            CommandLine.populateCommand(command, args);
        }
        command.files = Arrays.asList(files);
        return command;
    }
}
