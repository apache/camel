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
package org.apache.camel.component.mcp.server.jbang;

import org.apache.camel.main.BaseMainSupport;
import org.apache.camel.main.MainListenerSupport;

/**
 * Starts {@link JbangDevMcpServer} on the management HTTP server when {@code camel.management.mcpEnabled} is set.
 * Loaded at runtime when {@code camel-mcp-server} is on the classpath (e.g. {@code camel run --mcp}).
 */
public class JbangDevMcpMainListener extends MainListenerSupport {

    @Override
    public void afterConfigure(BaseMainSupport main) {
        if (!main.configure().httpManagementServer().isMcpEnabled()) {
            return;
        }
        JbangDevMcpServer server = new JbangDevMcpServer();
        server.setCamelContext(main.getCamelContext());
        server.setPath(main.configure().httpManagementServer().getMcpPath());
        try {
            main.getCamelContext().addService(server, true, true);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to register JBang dev MCP server", e);
        }
    }
}
