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
package org.apache.camel.component.mcp.server.vertx.integration;

import org.apache.camel.component.mcp.server.McpServerConfiguration;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Runs the scenarios of {@link OfficialMcpModernConformanceIT} against the default protocol era, {@code dual}, where
 * every request is first classified as 2026-07-28 or legacy. The known gaps are the same as with {@code modern}.
 */
@EnabledIfSystemProperty(named = "official-mcp-conformance", matches = ".*")
class OfficialMcpDualConformanceIT extends OfficialMcpModernConformanceIT {

    @Override
    protected void configure(McpServerConfiguration configuration) {
        configuration.setProtocolEra("dual");
    }
}
