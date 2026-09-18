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
package org.apache.camel.dsl.jbang.core.commands.validate;

import java.nio.file.Path;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.StringPrinter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * camel validate yaml and camel validate source against the catalog and YAML DSL schema of another Camel version or
 * runtime (CAMEL-24711). Without options the CLI's own version answers, offline.
 */
class ValidateCamelVersionTest {

    private static final String ROUTE = Path.of("src/test/resources/route.yaml").toString();
    private static final String NO_EXTENSION = Path.of("src/test/resources/no-extension.yaml").toString();

    /** The file based Quarkus extension registry of the camel-jbang-core tests: no registry call. */
    private static String quarkusExtRegistry() {
        return "--quarkus-ext-registry="
               + Path.of("../camel-jbang-core/src/test/resources/registry.quarkus.io").toAbsolutePath().normalize().toUri();
    }

    private static StringPrinter printer;

    private static int yaml(String... args) throws Exception {
        printer = new StringPrinter();
        YamlValidateCommand cmd = new YamlValidateCommand(new CamelJBangMain().withPrinter(printer));
        CommandLine.populateCommand(cmd, args);
        return cmd.doCall();
    }

    private static int source(String... args) throws Exception {
        printer = new StringPrinter();
        SourceValidateCommand cmd = new SourceValidateCommand(new CamelJBangMain().withPrinter(printer));
        CommandLine.populateCommand(cmd, args);
        return cmd.doCall();
    }

    @Test
    void theOwnVersionValidatesOffline() throws Exception {
        assertThat(yaml(ROUTE)).isZero();
        assertThat(printer.getOutput()).contains("Validation success");
        assertThat(source(ROUTE)).isZero();
        assertThat(printer.getOutput()).contains("Validation success");

        // the plain catalog knows every component: a Camel component without an extension is not its business
        assertThat(yaml(NO_EXTENSION)).isZero();
    }

    @Test
    @DisabledIfSystemProperty(named = "ci.env.name", matches = ".*",
                              disabledReason = "Runs only local — downloads the catalog and camel-yaml-dsl jar of another version")
    void anotherCamelVersionValidatesWithItsSchemaAndCatalog() throws Exception {
        assertThat(yaml("--camel-version=4.18.0", ROUTE)).isZero();
        assertThat(printer.getOutput()).contains("Validation success");
        assertThat(source("--camel-version=4.18.0", ROUTE)).isZero();
        assertThat(printer.getOutput()).contains("Validation success");

        // the canonical schema exists from 4.22: an older version is an error, not a silent fallback
        assertThatThrownBy(() -> yaml("--canonical", "--camel-version=4.18.0", ROUTE))
                .hasMessageContaining("no canonical YAML DSL schema").hasMessageContaining("4.22");
    }

    @Test
    @DisabledIfSystemProperty(named = "ci.env.name", matches = ".*",
                              disabledReason = "Runs only local — downloads the Quarkus platform BOM and catalog")
    void quarkusReportsAComponentWithoutAnExtension() throws Exception {
        // Quarkus platform 3.30.1 pins Camel 4.16.0: the catalog of camel-quarkus-catalog 3.30.0, the schema of 4.16.0
        assertThat(yaml("--runtime=quarkus", "--quarkus-version=3.30.1", quarkusExtRegistry(), ROUTE)).isZero();
        assertThat(source("--runtime=quarkus", "--quarkus-version=3.30.1", quarkusExtRegistry(), ROUTE)).isZero();

        assertThat(yaml("--runtime=quarkus", "--quarkus-version=3.30.1", quarkusExtRegistry(), NO_EXTENSION)).isEqualTo(1);
        assertThat(printer.getOutput()).contains("atmosphere-websocket: Camel Quarkus has no extension");
        assertThat(source("--runtime=quarkus", "--quarkus-version=3.30.1", quarkusExtRegistry(), NO_EXTENSION))
                .isEqualTo(1);
        assertThat(printer.getOutput()).contains("atmosphere-websocket: Camel Quarkus has no extension");
    }
}
