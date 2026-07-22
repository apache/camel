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
package org.apache.camel.dsl.jbang.core.commands.mcp;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.mcp.server.FilterContext;
import io.quarkiverse.mcp.server.ToolFilter;
import io.quarkiverse.mcp.server.ToolManager;

/**
 * Filters tool visibility in {@code tools/list} responses based on the configured access level.
 * <p>
 * When security is enabled, tools whose access tier exceeds the configured level are hidden from discovery. This
 * prevents LLM clients from attempting to call tools they cannot execute.
 */
@ApplicationScoped
public class McpAccessFilter implements ToolFilter {

    @Inject
    McpSecurityConfig config;

    @Override
    public boolean test(ToolManager.ToolInfo tool, FilterContext context) {
        if (!config.isEnabled()) {
            return true;
        }
        return config.getAccessLevel().permits(
                tool.annotations().map(ToolManager.ToolAnnotations::readOnlyHint).orElse(true),
                tool.annotations().map(ToolManager.ToolAnnotations::destructiveHint).orElse(false));
    }
}
