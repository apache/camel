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
package org.apache.camel.dsl.jbang.run;

import java.nio.file.Path;
import java.util.Properties;

import org.apache.camel.main.KameletMain;
import org.apache.camel.main.Main;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RunPipelineTest {

    @TempDir
    Path dir;

    private RunRequest request() {
        RunRequest request = new RunRequest();
        request.workDir = dir.resolve("work");
        request.files.add("route.camel.yaml");
        return request;
    }

    @Test
    void shouldAlignManagementPortWithAppPortForOpenApiUi() {
        RunRequest request = request();
        request.openapiUi = true;
        request.port = 9090;

        new RunPipeline(request).prepareOpenApiUiServerOptions();

        assertThat(request.port).isEqualTo(9090);
        assertThat(request.managementPort).isEqualTo(9090);
    }

    @Test
    void shouldPreserveExplicitManagementPortForOpenApiUi() {
        RunRequest request = request();
        request.openapiUi = true;
        request.port = 9090;
        request.managementPort = 9999;

        new RunPipeline(request).prepareOpenApiUiServerOptions();

        assertThat(request.port).isEqualTo(9090);
        assertThat(request.managementPort).isEqualTo(9999);
    }

    @Test
    void shouldApplyOpenApiUiOverrideProperties() {
        RunRequest request = request();
        request.openapiUi = true;
        Main main = new Main();

        new RunPipeline(request).applyOpenApiUiRuntimeOptions(main);

        assertThat(main.getOverrideProperties().getProperty("camel.rest.component")).isEqualTo("platform-http");
        assertThat(main.getOverrideProperties().getProperty("camel.rest.apiContextPath")).isEqualTo("/q/openapi.json");
        assertThat(main.getOverrideProperties().getProperty("camel.management.openapiUiEnabled")).isEqualTo("true");
        assertThat(main.getOverrideProperties().getProperty("camel.server.enabled")).isEqualTo("true");
        assertThat(main.getOverrideProperties().getProperty("camel.management.enabled")).isEqualTo("true");
    }

    @Test
    void applyMcpStdioOverridesProfileHttpTransport() {
        RunRequest request = request();
        request.mcpStdio = true;
        request.mcpTags = "agent";
        KameletMain main = new KameletMain("jbang");
        Properties profile = new Properties();
        profile.setProperty("camel.server.mcp-transport", "http");
        profile.setProperty("camel.server.mcp-enabled", "false");

        new RunPipeline(request).applyMcpStdioRuntimeOptions(main, profile);

        assertThat(main.getOverrideProperties().getProperty("camel.server.mcp-transport")).isEqualTo("stdio");
        assertThat(main.getOverrideProperties().getProperty("camel.server.mcp-enabled")).isEqualTo("true");
        assertThat(main.getOverrideProperties().getProperty("camel.server.mcp-tags")).isEqualTo("agent");
        assertThat(main.getOverrideProperties().getProperty("camel.main.startupSummaryLevel")).isEqualTo("Off");
    }

    @Test
    void mcpStdioIsExclusiveFromDevMcpFlag() {
        RunRequest request = request();
        request.mcpStdio = true;
        request.mcp = true;
        KameletMain main = new KameletMain("jbang");

        assertThatThrownBy(() -> new RunPipeline(request).applyMcpStdioRuntimeOptions(main, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--mcp-stdio and --mcp cannot be used together");
    }

    @Test
    void mcpStdioIsExclusiveFromProfileDevMcpFlag() {
        RunRequest request = request();
        request.mcpStdio = true;
        KameletMain main = new KameletMain("jbang");
        Properties profile = new Properties();
        profile.setProperty("camel.jbang.mcp", "true");

        assertThatThrownBy(() -> new RunPipeline(request).applyMcpStdioRuntimeOptions(main, profile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--mcp-stdio and --mcp cannot be used together");
    }

    @Test
    void applyMcpTagsOverrideForHttpAiToolServer() {
        RunRequest request = request();
        request.mcpTags = "crm,notify";
        KameletMain main = new KameletMain("jbang");
        Properties profile = new Properties();
        profile.setProperty("camel.server.mcp-enabled", "true");
        RunPipeline pipeline = new RunPipeline(request);

        assertThat(pipeline.isAiToolMcpServerEnabled(profile)).isTrue();
        pipeline.applyMcpTagsOverride(main);

        assertThat(main.getOverrideProperties().getProperty("camel.server.mcp-tags")).isEqualTo("crm,notify");
    }

    @Test
    void mcpStdioOfTheProfile() {
        Properties profile = new Properties();
        profile.setProperty("camel.server.mcp-transport", " STDIO ");

        assertThat(RunPipeline.isMcpStdioEnabled(false, profile)).isTrue();
        assertThat(RunPipeline.isMcpStdioEnabled(true, null)).isTrue();
        assertThat(RunPipeline.isMcpStdioEnabled(false, null)).isFalse();
    }
}
