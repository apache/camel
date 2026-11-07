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

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.camel.dsl.jbang.run.common.StringPrinter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The chaos verdict as the exit code of camel run --chaos: 1 for FAIL, and a line when there is no verdict.
 */
class ChaosVerdictTest {

    @TempDir
    Path dir;

    private final StringPrinter printer = new StringPrinter();

    private Path report(String json) throws Exception {
        return Files.writeString(dir.resolve("chaos.json"), json);
    }

    private static String rule(String outcome) {
        String expectation = outcome != null
                ? ", \"expectation\": {\"route\": \"ship\", \"outcome\": \"" + outcome + "\"}" : "";
        return "{\"id\": \"r1\", \"rule\": \"endpoint=http:stock* error\"" + expectation + "}";
    }

    @Test
    void failIsExitCodeOneWithTheReasons() throws Exception {
        Path file = report("{\"verdict\": \"FAIL\", \"reasons\": [\"route ship completed 3 of 10 exchanges\"],"
                           + " \"rules\": [" + rule("unmet") + "]}");

        assertThat(ChaosVerdict.apply(file, 0, printer)).isEqualTo(1);
        assertThat(printer.getStderr()).contains("Chaos verdict: FAIL", "route ship completed 3 of 10 exchanges");
        // a run that failed keeps its own exit code
        assertThat(ChaosVerdict.apply(file, 130, new StringPrinter())).isEqualTo(130);
    }

    @Test
    void passWithTheExpectationsThatWereMet() throws Exception {
        Path file = report("{\"verdict\": \"PASS\", \"reasons\": [], \"rules\": [" + rule("met") + "]}");

        assertThat(ChaosVerdict.apply(file, 0, printer)).isZero();
        assertThat(printer.getOutput()).contains("Chaos verdict: PASS (1 expectation met)");
    }

    @Test
    void withoutExpectationsThereIsNoVerdict() throws Exception {
        assertThat(ChaosVerdict.apply(report("{\"verdict\": \"PASS\", \"rules\": [" + rule(null) + "]}"), 0, printer))
                .isZero();
        assertThat(printer.getOutput()).contains("Chaos: no verdict, as no rule has an expectation (expect=)");

        StringPrinter cancelled = new StringPrinter();
        assertThat(ChaosVerdict.apply(report("{\"verdict\": \"PASS\", \"rules\": [" + rule("cancelled") + "]}"), 0,
                cancelled)).isZero();
        assertThat(cancelled.getOutput()).contains("the run ended before the expectations were decided");
    }

    @Test
    void nothingToSayWithoutRulesOrReport() throws Exception {
        assertThat(ChaosVerdict.apply(report("{\"verdict\": \"PASS\", \"rules\": []}"), 0, printer)).isZero();
        assertThat(ChaosVerdict.apply(dir.resolve("missing.json"), 0, printer)).isZero();
        assertThat(ChaosVerdict.apply(report("not json"), 0, printer)).isZero();
        // a rule that is not an object, or an expectation that is not one, says nothing
        assertThat(ChaosVerdict.apply(report("{\"verdict\": \"PASS\", \"rules\": [1, {\"expectation\": \"x\"}]}"), 0,
                printer)).isZero();
        assertThat(printer.getOutput()).doesNotContain("Chaos verdict");
    }
}
