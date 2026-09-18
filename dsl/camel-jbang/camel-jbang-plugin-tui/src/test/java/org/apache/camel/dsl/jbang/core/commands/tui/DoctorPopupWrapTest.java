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

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the word wrapping used for the detail rows of the Doctor popup.
 */
class DoctorPopupWrapTest {

    @Test
    void wrapsOnWordBoundaries() {
        List<String> lines = DoctorPopup.wrapWords(
                "Set ANTHROPIC_API_KEY, AZURE_OPENAI_*, GEMINI_API_KEY, OPENAI_API_KEY, WATSONX_APIKEY, or start Ollama",
                41);
        assertThat(lines).containsExactly(
                "Set ANTHROPIC_API_KEY, AZURE_OPENAI_*,",
                "GEMINI_API_KEY, OPENAI_API_KEY,",
                "WATSONX_APIKEY, or start Ollama");
        assertThat(lines).allSatisfy(l -> assertThat(l.length()).isLessThanOrEqualTo(41));
    }

    @Test
    void shortTextStaysOnOneLine() {
        assertThat(DoctorPopup.wrapWords("No AI client connected", 41)).containsExactly("No AI client connected");
    }

    @Test
    void longWordIsKeptWhole() {
        assertThat(DoctorPopup.wrapWords("a bbbbbbbbbbbbbbbbbbbb c", 10)).containsExactly("a", "bbbbbbbbbbbbbbbbbbbb", "c");
    }

    @Test
    void blankTextProducesNoRows() {
        assertThat(DoctorPopup.wrapWords("  ", 41)).isEmpty();
        assertThat(DoctorPopup.wrapWords(null, 41)).isEmpty();
    }
}
