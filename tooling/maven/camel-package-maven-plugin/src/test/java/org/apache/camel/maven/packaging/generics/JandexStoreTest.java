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
package org.apache.camel.maven.packaging.generics;

import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JandexStoreTest {

    @Test
    void doesNotExistReturnsTrueForNoSuchFileException() {
        NoSuchFileException cause = new NoSuchFileException("/some/path/jandex.idx");
        JandexStore.Jandex jandex = new JandexStore.Jandex(cause);

        assertThat(jandex.doesNotExist()).isTrue();
        assertThat(jandex.getException()).isSameAs(cause);
        assertThat(jandex.getIndex()).isNull();
    }

    @Test
    void doesNotExistReturnsFalseForOtherExceptions() {
        Exception cause = new RuntimeException("some other I/O error");
        JandexStore.Jandex jandex = new JandexStore.Jandex(cause);

        assertThat(jandex.doesNotExist()).isFalse();
        assertThat(jandex.getException()).isSameAs(cause);
    }

    @Test
    void readReturnsDoesNotExistForMissingJandexFile() {
        // A path that does not exist on disk
        Path nonExistent = Paths.get(System.getProperty("java.io.tmpdir"), "camel-test-missing-jandex-" + System.nanoTime());
        JandexStore.Jandex jandex = JandexStore.nonCachedRead(nonExistent);

        assertThat(jandex.doesNotExist())
                .as("Expected doesNotExist() to be true for a missing jandex file")
                .isTrue();
        assertThat(jandex.getIndex()).isNull();
    }
}
