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
package org.apache.camel.dsl.jbang.core.commands.generate;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.dsl.jbang.core.common.CommandLineTestHelper;
import org.apache.camel.dsl.jbang.run.common.StringPrinter;
import org.apache.camel.main.download.DependencyDownloaderClassLoader;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * camel generate schema prints the schema on stdout, and with --verbose what it does on stderr, so stdout can be
 * redirected to a schema file (CAMEL-26933). The class comes from the test class path instead of downloaded component
 * dependencies.
 */
class CodeSchemaGeneratorTest {

    private final StringPrinter printer = new StringPrinter();

    @Test
    void verboseKeepsStdoutTheSchema() throws Exception {
        int exit = generate("timer", Order.class.getName(), "--verbose");

        assertThat(exit).as(printer.getOutput()).isZero();
        JsonObject schema = (JsonObject) Jsoner.deserialize(printer.getStdout());
        JsonObject properties = (JsonObject) schema.get("properties");
        assertThat(properties).containsKeys("id", "amount");
        assertThat(printer.getStderr()).contains("Generating JSON Schema for component: timer",
                "Schema generation completed successfully");
    }

    @Test
    void classNotFoundSaysSoOnce() throws Exception {
        int exit = generate("timer", "org.acme.NoSuchClass");

        assertThat(exit).isEqualTo(CliExitCode.NOT_FOUND);
        assertThat(printer.getStdout()).isEmpty();
        // one error line, the hints after it without an ERROR of their own
        assertThat(printer.getStderr()).containsOnlyOnce("ERROR")
                .contains("ERROR: Class 'org.acme.NoSuchClass' not found in component dependencies", "Possible causes:");
    }

    private int generate(String component, String className, String... options) throws Exception {
        CodeSchemaGenerator command = new CodeSchemaGenerator(new CamelJBangMain().withPrinter(printer)) {
            @Override
            DependencyDownloaderClassLoader getDependencyDownloaderClassLoader(
                    String camelComponent, String version, boolean verbose) {
                return new DependencyDownloaderClassLoader(CodeSchemaGeneratorTest.class.getClassLoader());
            }
        };
        String[] args = new String[options.length + 2];
        args[0] = component;
        args[1] = className;
        System.arraycopy(options, 0, args, 2, options.length);
        CommandLineTestHelper.parse(command, args);
        return command.doCall();
    }

    public static class Order {
        private String id;
        private int amount;

        public String getId() {
            return id;
        }

        public int getAmount() {
            return amount;
        }
    }
}
