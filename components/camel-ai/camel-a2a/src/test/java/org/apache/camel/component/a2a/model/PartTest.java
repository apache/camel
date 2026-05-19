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
package org.apache.camel.component.a2a.model;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.component.a2a.util.A2AJsonMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PartTest {

    private final ObjectMapper mapper = A2AJsonMapper.instance();

    @Test
    void serializesWithoutLegacyKind() throws Exception {
        JsonNode json = mapper.valueToTree(new TextPart("hello"));

        assertThat(json.has("kind")).isFalse();
        assertThat(json.get("text").asText()).isEqualTo("hello");
    }

    @Test
    void deserializesExplicitKind() throws Exception {
        Part<?> part = mapper.readValue("{\"kind\":\"data\",\"data\":{\"status\":\"ok\"}}", Part.class);

        assertThat(part).isInstanceOf(DataPart.class);
        Map<?, ?> data = (Map<?, ?>) ((DataPart) part).data();
        assertThat(data.get("status")).isEqualTo("ok");
    }

    @Test
    void deserializesLegacyFieldOnlyPart() throws Exception {
        Part<?> part = mapper.readValue("{\"text\":\"hello\"}", Part.class);

        assertThat(part).isInstanceOf(TextPart.class);
        assertThat(((TextPart) part).text()).isEqualTo("hello");
    }

    @Test
    void rejectsMultiplePartContentFields() {
        assertThatThrownBy(() -> mapper.readValue("{\"text\":\"hello\",\"data\":{\"status\":\"ok\"}}", Part.class))
                .hasMessageContaining("A2A part must contain exactly one content field");
    }

    @Test
    void rejectsFilePartWithoutContent() {
        assertThatThrownBy(() -> mapper.readValue("{\"kind\":\"file\",\"filename\":\"empty.txt\"}", Part.class))
                .hasMessageContaining("FilePart must contain exactly one of raw or url");
    }
}
