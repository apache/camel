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
package org.apache.camel.dsl.jbang.launcher;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FakeJavaTest {

    @TempDir
    Path tempDir;

    @Test
    void drainsStdoutAndStderrWithoutDeadlock() throws Exception {
        Path script = tempDir.resolve(FakeJava.WINDOWS ? "large-output.bat" : "large-output.sh");
        Files.writeString(script, largeOutputScript(), StandardCharsets.UTF_8);

        FakeJava.Result result = assertTimeoutPreemptively(Duration.ofSeconds(10),
                () -> FakeJava.run(script, Map.of()));

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("stdout-20000"));
        assertTrue(result.stderr().contains("stderr-20000"));
    }

    @Test
    void fakeJavaPreservesInputAndNormalStderr() throws Exception {
        Path java = FakeJava.writeFakeJava(tempDir, "java", "openjdk version \"17.0.2\"", 0, "STDIO");

        FakeJava.Result result = FakeJava.runWithInput(java, Map.of(), "route input\n", "version");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("RAN=STDIO"), result.stdout());
        assertTrue(result.stdout().contains("route input"), result.stdout());
        assertTrue(result.stderr().contains("STDERR=STDIO"), result.stderr());
    }

    private static String largeOutputScript() {
        if (FakeJava.WINDOWS) {
            return "@echo off\r\n"
                   + "for /L %%i in (1,1,20000) do echo stderr-%%i 1>&2\r\n"
                   + "for /L %%i in (1,1,20000) do echo stdout-%%i\r\n";
        }
        return "#!/bin/sh\n"
               + "i=1\n"
               + "while [ $i -le 20000 ]; do echo stderr-$i 1>&2; i=$((i + 1)); done\n"
               + "i=1\n"
               + "while [ $i -le 20000 ]; do echo stdout-$i; i=$((i + 1)); done\n";
    }
}
