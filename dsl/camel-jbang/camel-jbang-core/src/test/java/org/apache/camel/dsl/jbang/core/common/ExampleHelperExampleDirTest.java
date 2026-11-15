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
package org.apache.camel.dsl.jbang.core.common;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The JVM that runs an example in the background deletes the folder of the example when it ends, and only a folder the
 * CLI made for an example (CAMEL-26930).
 */
class ExampleHelperExampleDirTest {

    @TempDir
    Path tmp;

    @Test
    void anExampleFolderInTheTemporaryDirectory() throws Exception {
        Path example = Files.createDirectory(tmp.resolve(ExampleHelper.EXAMPLE_DIR_PREFIX + "123"));

        assertThat(ExampleHelper.exampleDir(example.toString(), tmp)).isEqualTo(example.toRealPath());
    }

    @Test
    void anyOtherFolderIsLeftAlone() throws Exception {
        Path other = Files.createDirectory(tmp.resolve("my-project"));
        Path nested = Files.createDirectories(tmp.resolve("x").resolve(ExampleHelper.EXAMPLE_DIR_PREFIX + "1"));

        assertThat(ExampleHelper.exampleDir(other.toString(), tmp)).isNull();
        // not directly in the temporary directory
        assertThat(ExampleHelper.exampleDir(nested.toString(), tmp)).isNull();
        assertThat(ExampleHelper.exampleDir(tmp.resolve(ExampleHelper.EXAMPLE_DIR_PREFIX + "gone").toString(), tmp))
                .isNull();
        assertThat(ExampleHelper.exampleDir(null, tmp)).isNull();
        assertThat(ExampleHelper.exampleDir("", tmp)).isNull();
    }
}
