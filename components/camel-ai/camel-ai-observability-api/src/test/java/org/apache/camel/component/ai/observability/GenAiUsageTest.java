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
package org.apache.camel.component.ai.observability;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GenAiUsageTest {

    @Test
    void shouldCreateUsageWithLongTokenCounts() {
        GenAiUsage usage = GenAiUsage.of(100L, 50L, "stop", "gpt-4o");

        assertThat(usage.inputTokens()).isEqualTo(100L);
        assertThat(usage.outputTokens()).isEqualTo(50L);
        assertThat(usage.finishReason()).isEqualTo("stop");
        assertThat(usage.responseModel()).isEqualTo("gpt-4o");
    }

    @Test
    void shouldAcceptTokenCountsBeyondIntegerMaxValue() {
        long largeInput = Integer.MAX_VALUE + 1024L;
        long largeOutput = Integer.MAX_VALUE + 2048L;

        GenAiUsage usage = GenAiUsage.of(largeInput, largeOutput, "length", "gpt-4.1");

        assertThat(usage.inputTokens()).isEqualTo(largeInput);
        assertThat(usage.outputTokens()).isEqualTo(largeOutput);
    }

    @Test
    void shouldConvertIntegerFactoryArgumentsToLong() {
        GenAiUsage usage = GenAiUsage.of(12, 8, "stop", "gpt-4o-mini");

        assertThat(usage.inputTokens()).isEqualTo(12L);
        assertThat(usage.outputTokens()).isEqualTo(8L);
    }

    @Test
    void shouldAllowNullTokenCountsAndFinishReason() {
        GenAiUsage usage = GenAiUsage.of((Long) null, null, null, "gpt-4o");

        assertThat(usage.inputTokens()).isNull();
        assertThat(usage.outputTokens()).isNull();
        assertThat(usage.finishReason()).isNull();
        assertThat(usage.responseModel()).isEqualTo("gpt-4o");
    }

    @Test
    void shouldConvertNullIntegerFactoryArgumentsToNullLongFields() {
        GenAiUsage usage = GenAiUsage.of((Integer) null, (Integer) null, null, "gpt-4o");

        assertThat(usage.inputTokens()).isNull();
        assertThat(usage.outputTokens()).isNull();
    }

    @Test
    void shouldStringifyNonStringFinishReason() {
        GenAiUsage usage = GenAiUsage.of(1L, 2L, FinishReason.STOP, "model");

        assertThat(usage.finishReason()).isEqualTo("STOP");
    }

    private enum FinishReason {
        STOP
    }
}
