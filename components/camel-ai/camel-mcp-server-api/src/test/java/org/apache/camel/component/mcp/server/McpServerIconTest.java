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
package org.apache.camel.component.mcp.server;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpServerIconTest {

    @Test
    void testRejectsBlankSrc() {
        assertThatThrownBy(() -> new McpServerIcon(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("src");
    }

    @Test
    void testSizesAreDefensivelyCopied() {
        List<String> sizes = new ArrayList<>(List.of("48x48"));
        McpServerIcon icon = new McpServerIcon("https://example.com/icon.png", "image/png", sizes, "light");

        sizes.add("96x96");
        assertThat(icon.sizes()).containsExactly("48x48");
    }
}
