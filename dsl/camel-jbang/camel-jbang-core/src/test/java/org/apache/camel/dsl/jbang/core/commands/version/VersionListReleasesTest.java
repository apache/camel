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
package org.apache.camel.dsl.jbang.core.commands.version;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.apache.camel.dsl.jbang.core.commands.CamelCommandBaseTestSupport;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * camel version list without downloads: the Camel releases the catalog knows, here the 4.10 to 4.12 releases, which do
 * not change any more, filtered and formatted.
 */
class VersionListReleasesTest extends CamelCommandBaseTestSupport {

    @TempDir
    Path home;

    private Path previousHome;

    @BeforeEach
    void useHome() {
        // without the file that says when the list was updated
        previousHome = CommandLineHelper.getHomeDir();
        CommandLineHelper.useHomeDir(home.toString());
    }

    @AfterEach
    void restoreHome() {
        CommandLineHelper.useHomeDir(previousHome.toString());
    }

    @Test
    void theReleasesOfTheCatalog() throws Exception {
        assertThat(command().doCall()).isZero();

        String[] lines = printer.getStdout().split(System.lineSeparator());
        assertThat(lines[0]).contains("CAMEL VERSION", "JDK", "KIND", "RELEASED", "SUPPORTED UNTIL", "DAYS");
        // 4.10.0 to 4.10.9, 4.11.0 and 4.12.0
        assertThat(lines).hasSize(13);
        assertThat(lines[1]).contains("4.10.0", "17,21", "LTS", "February 2025", "February 2026");
        assertThat(lines[11]).contains("4.11.0", "April 2025").doesNotContain("LTS");
        assertThat(lines[12]).contains("4.12.0", "May 2025");
        // not part of the table
        assertThat(printer.getStderr()).isEqualTo("Last updated: none (use --fresh to update list from internet)");
    }

    @Test
    void json() throws Exception {
        assertThat(command("--json", "--to-version=4.10.1").doCall()).isZero();

        JsonArray releases = Jsoner.deserialize(printer.getStdout(), (JsonArray) null);
        assertThat(releases).hasSize(1);
        JsonObject release = releases.getMap(0);
        assertThat(release.getString("camelVersion")).isEqualTo("4.10.0");
        assertThat(release.getString("runtime")).isEqualTo("main");
        assertThat(release.getString("jdkVersion")).isEqualTo("17,21");
        assertThat(release.getString("kind")).isEqualTo("lts");
        assertThat(release.getString("releaseDate")).isEqualTo("2025-02-11");
        assertThat(release.getString("eolDate")).isEqualTo("2026-02-10");
        // the JSON is the output
        assertThat(printer.getStderr()).isEmpty();
    }

    @Test
    void jsonWithoutReleasesIsAnEmptyArray() throws Exception {
        assertThat(command("--json", "--from-date=2030-01-01").doCall()).isZero();

        assertThat(printer.getStdout()).isEqualTo("[]");
    }

    @Test
    void withoutPatchReleases() throws Exception {
        assertThat(command("--patch=false").doCall()).isZero();

        assertThat(versions()).containsExactly("4.10.0", "4.11.0", "4.12.0");
    }

    @Test
    void ltsOnly() throws Exception {
        assertThat(command("--lts", "--patch=false").doCall()).isZero();

        assertThat(versions()).containsExactly("4.10.0");
    }

    @Test
    void withoutTheReleasesAtTheirEndOfLife() throws Exception {
        // the 4.10 releases are supported until February 2026; the others have no end of life
        assertThat(command("--eol=false").doCall()).isZero();

        assertThat(versions()).containsExactly("4.11.0", "4.12.0");
    }

    @Test
    void releasedBetweenTwoDates() throws Exception {
        assertThat(command("--from-date=2025-04-01", "--to-date=2025-06-01").doCall()).isZero();

        assertThat(versions()).containsExactly("4.10.4", "4.11.0", "4.12.0");
    }

    @Test
    void theNewestFirstAndTheTail() throws Exception {
        assertThat(command("--sort=-version", "--tail=2").doCall()).isZero();

        // the end of the table: the oldest two
        assertThat(versions()).containsExactly("4.10.1", "4.10.0");
    }

    @Test
    void theDateFormat() throws Exception {
        assertThat(command("--date-format=yyyy-MM-dd", "--days=false", "--to-version=4.10.1").doCall()).isZero();

        String[] lines = printer.getStdout().split(System.lineSeparator());
        assertThat(lines[0]).doesNotContain("DAYS");
        assertThat(lines[1]).contains("4.10.0", "2025-02-11", "2026-02-10");
    }

    @Test
    void aDateFormatThatIsNotAPatternIsAUsageError() {
        VersionList command = command("--date-format=yyyy-bb");

        assertThatThrownBy(command::doCall)
                .isInstanceOfSatisfying(CliException.class,
                        e -> assertThat(e.getExitCode()).isEqualTo(CliExitCode.USAGE))
                .hasMessage("--date-format must be a date pattern such as dd-MM-yyyy, was: yyyy-bb");
    }

    @Test
    void aFromDateThatIsNotADateIsAUsageError() {
        VersionList command = command("--from-date=last year");

        assertThatThrownBy(command::doCall)
                .isInstanceOfSatisfying(CliException.class,
                        e -> assertThat(e.getExitCode()).isEqualTo(CliExitCode.USAGE))
                .hasMessage("--from-date must be a date such as 2025-01-31, was: last year");
    }

    @Test
    void aToDateThatIsNotADateIsAUsageError() {
        VersionList command = command("--to-date=31-12-2025");

        assertThatThrownBy(command::doCall)
                .isInstanceOfSatisfying(CliException.class,
                        e -> assertThat(e.getExitCode()).isEqualTo(CliExitCode.USAGE))
                .hasMessage("--to-date must be a date such as 2025-01-31, was: 31-12-2025");
    }

    // the 4.10 to 4.12 releases, without downloading the releases since the catalog
    private VersionList command(String... args) {
        VersionList command = new VersionList(new CamelJBangMain().withPrinter(printer));
        List<String> all = Stream.concat(Stream.of("--download=false", "--from-version=4.10", "--to-version=4.13"),
                Arrays.stream(args)).toList();
        // the last of an option given twice wins, as the arguments say
        CommandLine cl = new CommandLine(command);
        cl.setOverwrittenOptionsAllowed(true);
        cl.parseArgs(all.toArray(String[]::new));
        return command;
    }

    private List<String> versions() {
        return Arrays.stream(printer.getStdout().split(System.lineSeparator())).skip(1)
                .map(l -> l.strip().split("\\s+")[0]).toList();
    }
}
