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

import java.util.Map;

import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticDetailsTest {
    @Test
    void displayTextRemovesControlAndFormatCharactersWithoutChangingTheCapturedValue() {
        String captured = "Saf\u0000e\u001b\u007f\u0085\u202e\u200b \n\tlabel";
        JsonObject record = new JsonObject(Map.of("meaning", captured));
        assertThat(SemanticDetails.text(record, "meaning")).isEqualTo("Safe label");
        assertThat(record).containsEntry("meaning", captured);
    }

    @Test
    void displayTextPreservesPrintableUnicodeAndMissingValuePlaceholders() {
        JsonObject record = new JsonObject(Map.of("meaning", "Café — 東京", "count", 42));
        assertThat(SemanticDetails.text(record, "meaning")).isEqualTo("Café — 東京");
        assertThat(SemanticDetails.text(record, "count")).isEqualTo("42");
        assertThat(SemanticDetails.text(record, "missing")).isEqualTo("—");
        assertThat(SemanticDetails.text(null, "meaning")).isEqualTo("—");
    }
}
