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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * camel dependency update writes a file as it was: its line separator, and its last newline (CAMEL-26931).
 */
class DependencyUpdateWriteTest {

    @TempDir
    Path dir;

    @Test
    void keepsTheLastNewline() throws Exception {
        Path file = Files.writeString(dir.resolve("Hello.java"), "//DEPS a\nclass Hello {}\n");

        DependencyUpdate.writeLikeTheFile(file, List.of("//DEPS b", "class Hello {}"));

        assertThat(file).hasContent("//DEPS b\nclass Hello {}\n");
        assertThat(Files.readString(file)).endsWith("}\n").doesNotContain("\r");
    }

    @Test
    void keepsCrlf() throws Exception {
        Path file = Files.writeString(dir.resolve("Hello.java"), "//DEPS a\r\nclass Hello {}\r\n");

        DependencyUpdate.writeLikeTheFile(file, List.of("//DEPS b", "class Hello {}"));

        assertThat(Files.readString(file)).isEqualTo("//DEPS b\r\nclass Hello {}\r\n");
    }

    @Test
    void noNewlineWhenTheFileHadNone() throws Exception {
        Path file = Files.writeString(dir.resolve("Hello.java"), "//DEPS a\nclass Hello {}");

        DependencyUpdate.writeLikeTheFile(file, List.of("//DEPS b", "class Hello {}"));

        assertThat(Files.readString(file)).isEqualTo("//DEPS b\nclass Hello {}");
    }
}
