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
package org.apache.camel.dsl.jbang.core.common;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JsonOutputTest {

    @Test
    void errorDocument() {
        assertThat(JsonOutput.error(CliExitCode.NOT_FOUND, "No running Camel integration matches: foo").toJson())
                .isEqualTo("{\"status\":\"error\",\"code\":3,\"message\":\"No running Camel integration matches: foo\"}");
    }

    @Test
    void emptyListIsAnEmptyArray() throws Exception {
        StringPrinter printer = new StringPrinter();
        JsonOutput.printList(printer, null);
        JsonOutput.printList(printer, List.of());

        assertThat(printer.getLines()).containsExactly("[]", "[]");
    }

    @Test
    void jsonModeFromTheCommandLine() {
        assertThat(JsonOutput.isRequested("get", "route", "--json")).isTrue();
        assertThat(JsonOutput.isRequested("--json=true")).isTrue();
        assertThat(JsonOutput.isRequested("get", "route")).isFalse();
        assertThat(JsonOutput.isRequested("--json=false")).isFalse();
        // after -- the arguments are not options of the command
        assertThat(JsonOutput.isRequested("run", "--", "--json")).isFalse();
        assertThat(JsonOutput.isRequested((String[]) null)).isFalse();
    }
}
