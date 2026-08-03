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
package org.apache.camel.dsl.jbang.core.commands;

import org.apache.camel.main.Main;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

class RunOpenApiUiOptionsTest {

    @Test
    void shouldParseOpenApiUiFlag() {
        Run run = new Run(new CamelJBangMain());
        CommandLine cmd = new CommandLine(run);

        cmd.parseArgs("--openapi-ui", "hello.java");

        assertThat(run.serverOptions.openapiUi).isTrue();
        assertThat(run.files).containsExactly("hello.java");
    }

    @Test
    void shouldAlignManagementPortWithAppPortForOpenApiUi() {
        Run run = new Run(new CamelJBangMain());
        new CommandLine(run).parseArgs("--openapi-ui", "--port=9090", "hello.java");

        run.prepareOpenApiUiServerOptions();

        assertThat(run.serverOptions.port).isEqualTo(9090);
        assertThat(run.serverOptions.managementPort).isEqualTo(9090);
    }

    @Test
    void shouldPreserveExplicitManagementPortForOpenApiUi() {
        Run run = new Run(new CamelJBangMain());
        new CommandLine(run).parseArgs("--openapi-ui", "--port=9090", "--management-port=9999", "hello.java");

        run.prepareOpenApiUiServerOptions();

        assertThat(run.serverOptions.port).isEqualTo(9090);
        assertThat(run.serverOptions.managementPort).isEqualTo(9999);
    }

    @Test
    void shouldApplyOpenApiUiOverrideProperties() {
        Run run = new Run(new CamelJBangMain());
        new CommandLine(run).parseArgs("--openapi-ui", "hello.java");
        Main main = new Main();

        run.applyOpenApiUiRuntimeOptions(main);

        assertThat(main.getOverrideProperties().getProperty("camel.rest.component")).isEqualTo("platform-http");
        assertThat(main.getOverrideProperties().getProperty("camel.rest.apiContextPath")).isEqualTo("/q/openapi.json");
        assertThat(main.getOverrideProperties().getProperty("camel.management.openapiUiEnabled")).isEqualTo("true");
        assertThat(main.getOverrideProperties().getProperty("camel.server.enabled")).isEqualTo("true");
        assertThat(main.getOverrideProperties().getProperty("camel.management.enabled")).isEqualTo("true");
    }
}
