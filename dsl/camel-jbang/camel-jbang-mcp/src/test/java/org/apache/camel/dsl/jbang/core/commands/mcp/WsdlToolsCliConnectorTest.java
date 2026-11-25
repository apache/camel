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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.camel.dsl.jbang.core.commands.mcp.WsdlTools.InspectResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-27511: the CamelContext that camel_wsdl_inspect starts for the samples must not start the CLI connector. The
 * connector names its lock file after the pid of the JVM, {@code ~/.camel/<pid>}, which every context of the JVM
 * shares. When the context of the tool closed, it deleted that lock, and the other contexts of the MCP server (or of
 * the tests) shut themselves down as if the Camel CLI had stopped them.
 * <p>
 * Isolated because it changes {@code user.home}, where the connector writes its files.
 */
@Isolated
class WsdlToolsCliConnectorTest {

    @TempDir
    Path userHome;

    private String previousUserHome;

    @BeforeEach
    void useHome() {
        previousUserHome = System.getProperty("user.home");
        System.setProperty("user.home", userHome.toString());
    }

    @AfterEach
    void restoreHome() {
        System.setProperty("user.home", previousUserHome);
    }

    @Test
    void theSamplesAreMadeWithoutTheCliConnector() throws Exception {
        String wsdl = Files.readString(Paths.get("src/test/resources/wsdl/orders.wsdl"));

        InspectResult result = new WsdlTools().camel_wsdl_inspect(wsdl, "getOrder", true);

        // the context did start: the samples are there
        assertThat(result.ports().get(0).operations().get(0).samples()).isNotEmpty();
        // and it neither wrote nor deleted the lock file of the JVM
        assertThat(userHome.resolve(".camel")).doesNotExist();
    }
}
