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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.dsl.jbang.run.common.Printer;
import org.apache.camel.util.json.DeserializationException;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/**
 * The verdict of the chaos rules as the exit code of camel run --chaos (CAMEL-26722), so chaos can gate a pipeline:
 * camel-chaos writes its report to a file when the run ends (camel.chaos.reportFile), and a run that ended well ends
 * with exit code 1 when the verdict is FAIL. Without an expectation there is no verdict, which a line says.
 */
final class ChaosVerdict {

    private ChaosVerdict() {
    }

    /**
     * The exit code of the run with the verdict of the report.
     *
     * @param  file     the report that camel-chaos wrote when the run ended
     * @param  exitCode the exit code of the run
     * @param  printer  where the verdict is said
     * @return          1 when the run ended with 0 and the verdict is FAIL, else the exit code of the run
     */
    static int apply(Path file, int exitCode, Printer printer) {
        JsonObject report = read(file);
        if (report == null) {
            // chaos was off, or the run did not get as far as starting Camel
            return exitCode;
        }
        List<Object> rules = report.getCollection("rules");
        if (rules == null || rules.isEmpty()) {
            // no rule was added: nothing to say
            return exitCode;
        }
        if ("FAIL".equals(report.getString("verdict"))) {
            StringBuilder sb = new StringBuilder("Chaos verdict: FAIL");
            List<Object> reasons = report.getCollection("reasons");
            if (reasons != null) {
                reasons.forEach(r -> sb.append("\n  ").append(r));
            }
            printer.printErr(sb.toString());
            return exitCode == CliExitCode.OK ? CliExitCode.FAILURE : exitCode;
        }
        int decided = 0;
        int cancelled = 0;
        for (Object o : rules) {
            JsonObject expectation = (JsonObject) ((JsonObject) o).get("expectation");
            if (expectation != null) {
                String outcome = expectation.getString("outcome");
                if ("cancelled".equals(outcome)) {
                    cancelled++;
                } else if ("met".equals(outcome) || "unmet".equals(outcome)) {
                    decided++;
                }
            }
        }
        if (decided > 0) {
            printer.println("Chaos verdict: PASS (" + decided + (decided == 1 ? " expectation" : " expectations") + " met)");
        } else if (cancelled > 0) {
            printer.println("Chaos: no verdict, as the run ended before the expectations were decided"
                            + " (make within= shorter than the run)");
        } else {
            printer.println("Chaos: no verdict, as no rule has an expectation (expect=)");
        }
        return exitCode;
    }

    private static JsonObject read(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return null;
        }
        try {
            return (JsonObject) Jsoner.deserialize(Files.readString(file));
        } catch (IOException | DeserializationException | ClassCastException e) {
            return null;
        }
    }
}
