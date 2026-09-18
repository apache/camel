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
package org.apache.camel.component.mcp.server.main;

import java.util.List;

import org.apache.camel.component.mcp.server.McpServerIcon;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpServerIconsParserTest {

    @Test
    void testParsesIconsArray() {
        List<McpServerIcon> icons = McpServerIconsParser.parse("""
                [
                  {
                    "src": "https://example.com/icon.png",
                    "mimeType": "image/png",
                    "sizes": ["48x48", "96x96"],
                    "theme": "light"
                  }
                ]
                """);

        assertThat(icons).hasSize(1);
        assertThat(icons.get(0).src()).isEqualTo("https://example.com/icon.png");
        assertThat(icons.get(0).mimeType()).isEqualTo("image/png");
        assertThat(icons.get(0).sizes()).containsExactly("48x48", "96x96");
        assertThat(icons.get(0).theme()).isEqualTo("light");
    }

    @Test
    void testBlankJsonReturnsNull() {
        assertThat(McpServerIconsParser.parse(null)).isNull();
        assertThat(McpServerIconsParser.parse("   ")).isNull();
    }

    @Test
    void testRejectsNonArrayJson() {
        assertThatThrownBy(() -> McpServerIconsParser.parse("{\"src\":\"https://example.com/icon.png\"}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JSON array");
    }

    @Test
    void testParsesSrcOnlyIcon() {
        List<McpServerIcon> icons = McpServerIconsParser.parse("""
                [{"src":"https://example.com/icon.png"}]
                """);

        assertThat(icons).hasSize(1);
        assertThat(icons.get(0).src()).isEqualTo("https://example.com/icon.png");
        assertThat(icons.get(0).mimeType()).isNull();
    }

    @Test
    void testRejectsNonObjectIconEntry() {
        assertThatThrownBy(() -> McpServerIconsParser.parse("[\"https://example.com/icon.png\"]"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JSON object");
    }

    @Test
    void testRejectsNonArraySizes() {
        assertThatThrownBy(() -> McpServerIconsParser.parse("""
                [{"src":"https://example.com/icon.png","sizes":"48x48"}]
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sizes");
    }

    @Test
    void testRejectsMissingSrc() {
        assertThatThrownBy(() -> McpServerIconsParser.parse("[{\"mimeType\":\"image/png\"}]"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("src");
    }
}
