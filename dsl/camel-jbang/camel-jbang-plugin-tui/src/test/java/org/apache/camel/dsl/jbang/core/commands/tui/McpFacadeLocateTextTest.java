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
import java.util.concurrent.atomic.AtomicReference;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Style;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class McpFacadeLocateTextTest {

    @Test
    void locateTextCountsColumnsAsTheScreenShowsThem() {
        RecordingManager recording = new RecordingManager(new CaptionOverlay());
        Buffer buffer = Buffer.empty(new Rect(0, 0, 20, 1));
        // an accented e written as e and a combining accent takes one column
        buffer.setString(0, 0, "e\u0301 target", Style.EMPTY);
        recording.updateBuffer(buffer);
        McpFacade facade = new McpFacade(
                null, new AtomicReference<>(List.of()), null, recording, null, null, null, null, null, null, null,
                null, null);

        JsonArray matches = facade.locateText("target");

        assertThat(matches).hasSize(1);
        assertThat(((JsonObject) matches.get(0)).getInteger("x")).isEqualTo(2);
        assertThat(((JsonObject) matches.get(0)).getInteger("width")).isEqualTo(6);
    }
}
