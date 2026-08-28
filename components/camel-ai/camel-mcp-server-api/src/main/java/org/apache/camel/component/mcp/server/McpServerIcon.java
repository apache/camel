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

import java.util.List;

/**
 * Icon metadata advertised in the MCP {@code serverInfo} block.
 *
 * @param src      icon URL (required)
 * @param mimeType optional MIME type (for example {@code image/png})
 * @param sizes    optional size descriptors (for example {@code 48x48})
 * @param theme    optional theme hint (for example {@code light} or {@code dark})
 *
 * @since          4.23
 */
public record McpServerIcon(String src, String mimeType, List<String> sizes, String theme) {

    public McpServerIcon {
        if (src == null || src.isBlank()) {
            throw new IllegalArgumentException("src must be set");
        }
        if (sizes != null) {
            sizes = List.copyOf(sizes);
        }
    }

    public McpServerIcon(String src) {
        this(src, null, null, null);
    }
}
