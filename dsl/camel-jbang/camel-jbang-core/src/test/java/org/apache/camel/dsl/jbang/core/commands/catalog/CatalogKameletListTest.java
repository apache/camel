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
package org.apache.camel.dsl.jbang.core.commands.catalog;

import java.io.IOException;
import java.util.List;

import org.apache.camel.dsl.jbang.core.commands.CamelCommandBaseTestSupport;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * camel catalog kamelet: the Kamelets of the catalog (here a few, instead of the download of the catalog), filtered by
 * type, name or description, as a table or JSON.
 */
class CatalogKameletListTest extends CamelCommandBaseTestSupport {

    @Test
    void theKameletsByName() throws Exception {
        assertThat(command().doCall()).isZero();

        String[] lines = printer.getOutput().split(System.lineSeparator());
        assertThat(lines[0]).contains("NAME", "TYPE", "LEVEL", "DESCRIPTION");
        assertThat(lines).hasSize(5);
        // the first sentence of the description
        assertThat(lines[1]).contains("aws-s3-sink", "sink", "Preview", "Writes to S3").doesNotContain("bucket");
        assertThat(lines[2]).contains("kafka-source", "source", "Stable", "Reads from Kafka");
        assertThat(lines[3]).contains("log-action", "action");
        assertThat(lines[4]).contains("timer-source", "source");
    }

    @Test
    void filterByType() throws Exception {
        assertThat(command("--type=SOURCE").doCall()).isZero();

        assertThat(printer.getOutput()).contains("kafka-source", "timer-source").doesNotContain("aws-s3-sink",
                "log-action");
    }

    @Test
    void unknownTypeIsAUsageError() {
        CatalogKamelet command = command("--type=sources");

        assertThatThrownBy(command::doCall)
                .isInstanceOfSatisfying(CliException.class,
                        e -> assertThat(e.getExitCode()).isEqualTo(CliExitCode.USAGE))
                .hasMessage("--type must be one of source, sink, action, was: sources");
    }

    @Test
    void filterByNameOrDescription() throws Exception {
        // the log-action Kamelet has no description
        assertThat(command("--filter=kafka").doCall()).isZero();

        assertThat(printer.getOutput()).contains("kafka-source").doesNotContain("timer-source", "log-action");
    }

    @Test
    void filterByTheWholeName() throws Exception {
        assertThat(command("--filter=Log-Action").doCall()).isZero();

        assertThat(printer.getOutput()).contains("log-action").doesNotContain("kafka-source");
    }

    @Test
    void noResultsForTheFilter() throws Exception {
        assertThat(command("--filter=mqtt").doCall()).isZero();

        assertThat(printer.getStdout()).isEmpty();
        assertThat(printer.getStderr()).isEqualTo("No results for filter: mqtt");
    }

    @Test
    void json() throws Exception {
        assertThat(command("--json", "--type=sink").doCall()).isZero();

        JsonArray kamelets = Jsoner.deserialize(printer.getStdout(), (JsonArray) null);
        assertThat(kamelets).hasSize(1);
        JsonObject s3 = kamelets.getMap(0);
        assertThat(s3.getString("name")).isEqualTo("aws-s3-sink");
        assertThat(s3.getString("type")).isEqualTo("sink");
        assertThat(s3.getString("level")).isEqualTo("Preview");
        // the whole description
        assertThat(s3.getString("description")).isEqualTo("Writes to S3. Each message is a file in the bucket.");
    }

    @Test
    void jsonWithoutResultsIsAnEmptyArray() throws Exception {
        assertThat(command("--json", "--filter=mqtt").doCall()).isZero();

        assertThat(printer.getStdout()).isEqualTo("[]");
        assertThat(printer.getStderr()).isEqualTo("No results for filter: mqtt");
    }

    @Test
    void aCatalogThatCannotBeDownloadedIsAFailure() throws Exception {
        CatalogKamelet command = new CatalogKamelet(new CamelJBangMain().withPrinter(printer)) {
            @Override
            protected List<KameletModel> loadKamelets() throws Exception {
                throw new IOException("no network");
            }
        };
        CommandLine.populateCommand(command, "--kamelets-version=4.22.1");

        assertThat(command.doCall()).isEqualTo(CliExitCode.FAILURE);

        assertThat(printer.getStderr()).isEqualTo("ERROR: Cannot download camel-kamelets-catalog due to no network");
    }

    private CatalogKamelet command(String... args) {
        CatalogKamelet command = new CatalogKamelet(new CamelJBangMain().withPrinter(printer)) {
            @Override
            protected List<KameletModel> loadKamelets() {
                return List.of(
                        model("timer-source", "source", "Stable", "Produces periodic events"),
                        model("kafka-source", "source", "Stable", "Reads from Kafka"),
                        model("aws-s3-sink", "sink", "Preview", "Writes to S3. Each message is a file in the bucket."),
                        model("log-action", "action", "Stable", null));
            }
        };
        String[] withVersion = new String[args.length + 1];
        withVersion[0] = "--kamelets-version=4.22.1";
        System.arraycopy(args, 0, withVersion, 1, args.length);
        CommandLine.populateCommand(command, withVersion);
        return command;
    }

    private static KameletModel model(String name, String type, String supportLevel, String description) {
        KameletModel m = new KameletModel();
        m.name = name;
        m.type = type;
        m.supportLevel = supportLevel;
        m.description = description;
        return m;
    }
}
