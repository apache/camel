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
package org.apache.camel.dsl.jbang.core.commands.kubernetes;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The --property values of camel kubernetes run: a key=value, or the properties of a file (CAMEL-26934).
 */
class KubernetesRunPropertiesTest {

    @TempDir
    Path dir;

    @Test
    void aValueThatIsAPathIsAProperty() throws Exception {
        // was taken for a file that does not exist, and left out
        assertArrayEquals(new String[] { "camel.server.path=/api", "greeting=hello" },
                KubernetesRun.mergeProperties(new String[] { "camel.server.path=/api", "greeting=hello" }));
    }

    @Test
    void thePropertiesOfAFile() throws Exception {
        Path file = Files.writeString(dir.resolve("my.properties"), "foo=bar\n");

        assertArrayEquals(new String[] { "foo=bar" }, KubernetesRun.mergeProperties(new String[] { file.toString() }));
        assertArrayEquals(new String[] { "foo=bar" }, KubernetesRun.mergeProperties(new String[] { "file:" + file }));
    }

    @Test
    void aPropertiesFileThatDoesNotExistIsNotFound() {
        CliException e = assertThrows(CliException.class,
                () -> KubernetesRun.mergeProperties(new String[] { dir.resolve("nope.properties").toString() }));
        assertEquals(CliExitCode.FILE_NOT_FOUND, e.getExitCode());
        assertTrue(e.getMessage().startsWith("Properties file does not exist: "), e.getMessage());
    }
}
