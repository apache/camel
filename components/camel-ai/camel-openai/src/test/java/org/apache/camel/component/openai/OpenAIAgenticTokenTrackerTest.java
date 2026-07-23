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
package org.apache.camel.component.openai;

import com.openai.models.completions.CompletionUsage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAIAgenticTokenTrackerTest {

    @Test
    void shouldAccumulatePromptAndCompletionTokens() {
        OpenAIAgenticTokenTracker tracker = new OpenAIAgenticTokenTracker();

        tracker.addUsage(CompletionUsage.builder()
                .promptTokens(30)
                .completionTokens(20)
                .totalTokens(50)
                .build());
        tracker.addUsage(CompletionUsage.builder()
                .promptTokens(10)
                .completionTokens(5)
                .totalTokens(15)
                .build());

        assertThat(tracker.getPromptTokens()).isEqualTo(40);
        assertThat(tracker.getCompletionTokens()).isEqualTo(25);
        assertThat(tracker.getTotalTokens()).isEqualTo(65);
    }
}
