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
package org.apache.camel.dsl.jbang.core.commands.action;

import java.util.Locale;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * camel cmd heap-dump prints the size of the dump the same way in every locale.
 */
class CamelHeapDumpTest {

    @Test
    void sizeIsTheSameInEveryLocale() {
        Locale locale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            assertThat(CamelHeapDump.formatSize(512)).isEqualTo("512 bytes");
            assertThat(CamelHeapDump.formatSize(1536)).isEqualTo("1.5 KB");
            assertThat(CamelHeapDump.formatSize(5L * 1024 * 1024 / 2)).isEqualTo("2.5 MB");
            assertThat(CamelHeapDump.formatSize(3L * 1024 * 1024 * 1024)).isEqualTo("3.0 GB");
        } finally {
            Locale.setDefault(locale);
        }
    }
}
