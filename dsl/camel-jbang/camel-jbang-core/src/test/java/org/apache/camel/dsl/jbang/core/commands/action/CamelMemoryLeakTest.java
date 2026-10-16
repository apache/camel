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

import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * camel cmd memory-leak --min-size: the size of the samples to show.
 */
class CamelMemoryLeakTest {

    @Test
    void sizes() {
        assertThat(CamelMemoryLeak.parseSize("0")).isZero();
        assertThat(CamelMemoryLeak.parseSize("1024")).isEqualTo(1024);
        assertThat(CamelMemoryLeak.parseSize("10B")).isEqualTo(10);
        assertThat(CamelMemoryLeak.parseSize("10KB")).isEqualTo(10 * 1024);
        assertThat(CamelMemoryLeak.parseSize("10k")).isEqualTo(10 * 1024);
        assertThat(CamelMemoryLeak.parseSize("1.5MB")).isEqualTo(1536 * 1024);
        assertThat(CamelMemoryLeak.parseSize("2G")).isEqualTo(2L * 1024 * 1024 * 1024);
    }

    @Test
    void notASizeIsAUsageError() {
        assertThatThrownBy(() -> CamelMemoryLeak.parseSize("lots"))
                .isInstanceOf(CliException.class)
                .hasMessage("--min-size must be a size such as 1024, 10KB or 1MB, was: lots")
                .extracting(e -> ((CliException) e).getExitCode()).isEqualTo(CliExitCode.USAGE);
    }
}
