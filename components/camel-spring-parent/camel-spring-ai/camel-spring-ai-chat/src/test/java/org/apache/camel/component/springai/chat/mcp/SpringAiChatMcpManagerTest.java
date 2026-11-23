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
package org.apache.camel.component.springai.chat.mcp;

import java.net.URI;

import io.modelcontextprotocol.util.Utils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpringAiChatMcpManagerTest {

    /**
     * A path that starts with // stays a path on the server of the URL where the SDK transports resolve the endpoint:
     * mcp.invalid is not read as a host. Tested on the split rather than end to end, as recent JDK updates of the HTTP
     * server of the JDK reject a request URI that starts with //.
     */
    @Test
    void pathStartingWithTwoSlashesStaysOnTheServerOfTheUrl() {
        SpringAiChatMcpManager.McpServerUrl serverUrl
                = SpringAiChatMcpManager.splitUrl("local", "http://127.0.0.1:8080//mcp.invalid/custom/sse", "/sse");

        assertThat(URI.create(serverUrl.endpoint()).getHost()).isEqualTo("127.0.0.1");
        URI resolved = Utils.resolveUri(URI.create(serverUrl.baseUri()), serverUrl.endpoint());
        assertThat(resolved.getHost()).isEqualTo("127.0.0.1");
        assertThat(resolved.getRawPath()).isEqualTo("//mcp.invalid/custom/sse");
    }
}
