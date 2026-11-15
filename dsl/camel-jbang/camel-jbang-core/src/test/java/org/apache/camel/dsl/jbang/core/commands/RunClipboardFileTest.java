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

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * camel run clipboard.java writes the clipboard into the work directory: a Java class was written into the current
 * directory, over a file of that name (CAMEL-26930).
 */
class RunClipboardFileTest {

    private final Path work = Path.of(".camel-jbang", "work");

    @Test
    void aJavaClassIsInTheWorkDirectoryUnderItsClassName() {
        assertThat(Run.clipboardFile(work, "java", "com.acme.OrderRoute")).isEqualTo(work.resolve("OrderRoute.java"));
        assertThat(Run.clipboardFile(work, "java", "OrderRoute")).isEqualTo(work.resolve("OrderRoute.java"));
    }

    @Test
    void otherFilesAreInTheWorkDirectory() {
        assertThat(Run.clipboardFile(work, "yaml", null)).isEqualTo(work.resolve("generated-clipboard.yaml"));
    }
}
