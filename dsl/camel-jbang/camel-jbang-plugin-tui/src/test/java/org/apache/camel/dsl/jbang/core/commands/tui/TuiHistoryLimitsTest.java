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
package org.apache.camel.dsl.jbang.core.commands.tui;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TuiHistoryLimitsTest {

    @Test
    void unsetUsesDefaultLimit() {
        assertThat(TuiHistoryLimits.resolveLimit(null)).isEqualTo(TuiHistoryLimits.DEFAULT_LIMIT);
        assertThat(TuiHistoryLimits.resolveLimit("   ")).isEqualTo(TuiHistoryLimits.DEFAULT_LIMIT);
    }

    @Test
    void zeroDisablesHistory() {
        assertThat(TuiHistoryLimits.resolveLimit("0")).isZero();
    }

    @Test
    void parsesPositiveLimit() {
        assertThat(TuiHistoryLimits.resolveLimit("100")).isEqualTo(100);
        assertThat(TuiHistoryLimits.resolveLimit(" 25 ")).isEqualTo(25);
    }

    @Test
    void negativeValuesClampToZero() {
        assertThat(TuiHistoryLimits.resolveLimit("-3")).isZero();
    }

    @Test
    void invalidValueFallsBackToDefault() {
        assertThat(TuiHistoryLimits.resolveLimit("many")).isEqualTo(TuiHistoryLimits.DEFAULT_LIMIT);
    }
}
