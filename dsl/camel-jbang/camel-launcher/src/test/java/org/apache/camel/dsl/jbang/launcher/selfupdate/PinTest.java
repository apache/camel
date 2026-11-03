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
package org.apache.camel.dsl.jbang.launcher.selfupdate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PinTest {

    @Test
    void parsesExactPin() {
        Pin pin = Pin.parse("4.22.2\n");

        assertThat(pin.kind()).isEqualTo(Pin.Kind.EXACT);
        assertThat(pin.value()).isEqualTo("4.22.2");
        assertThat(pin.tracksAlias()).isFalse();
        assertThat(pin.accepts("4.22.2")).isTrue();
        assertThat(pin.accepts("4.22.3")).isFalse();
        assertThat(pin.describe()).isEqualTo("version 4.22.2");
        assertThatThrownBy(pin::aliasName).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void parsesLinePin() {
        Pin pin = Pin.parse("4.22");

        assertThat(pin.kind()).isEqualTo(Pin.Kind.LINE);
        assertThat(pin.tracksAlias()).isTrue();
        assertThat(pin.aliasName()).isEqualTo("4.22");
        assertThat(pin.accepts("4.22.0")).isTrue();
        assertThat(pin.accepts("4.22.17")).isTrue();
        assertThat(pin.accepts("4.23.0")).isFalse();
        assertThat(pin.accepts("4.220.1")).isFalse();
        assertThat(pin.accepts("14.22.1")).isFalse();
        assertThat(pin.describe()).isEqualTo("the 4.22 release line");
    }

    @ParameterizedTest
    @ValueSource(strings = { "LTS", "lts", "Lts", " LTS\r\n" })
    void parsesLtsPinCaseInsensitively(String raw) {
        Pin pin = Pin.parse(raw);

        assertThat(pin.kind()).isEqualTo(Pin.Kind.LTS);
        assertThat(pin.value()).isEqualTo("LTS");
        assertThat(pin.tracksAlias()).isTrue();
        assertThat(pin.aliasName()).isEqualTo("lts");
        assertThat(pin.accepts("4.22.2")).isTrue();
        assertThat(pin.accepts("5.0.0")).isTrue();
        assertThat(pin.describe()).isEqualTo("LTS");
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "4", "4.", "4.22.", "4..22", "4.22.2.1", "4.22.2-SNAPSHOT", "latest", "LTS2", "v4.22" })
    void treatsAnythingElseAsInvalidAndNeverMoves(String raw) {
        Pin pin = Pin.parse(raw);

        assertThat(pin.kind()).isEqualTo(Pin.Kind.INVALID);
        assertThat(pin.tracksAlias()).isFalse();
        assertThat(pin.accepts("4.22.2")).isFalse();
    }
}
