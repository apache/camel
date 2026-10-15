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

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimeoutConverterTest {

    private final TimeoutConverter converter = new TimeoutConverter();

    @Test
    void numberIsMillis() {
        assertThat(converter.convert("20000")).isEqualTo(20000L);
    }

    @Test
    void durationHasUnits() {
        assertThat(converter.convert("30s")).isEqualTo(30_000L);
        assertThat(converter.convert("1m30s")).isEqualTo(90_000L);
        assertThat(converter.convert(" 2m ")).isEqualTo(120_000L);
    }

    @Test
    void anythingElseIsAnInvalidOption() {
        assertThatThrownBy(() -> converter.convert("soon"))
                .isInstanceOf(CommandLine.TypeConversionException.class)
                .hasMessage("'soon' is not a timeout: use millis, or a duration such as 30s");
    }
}
