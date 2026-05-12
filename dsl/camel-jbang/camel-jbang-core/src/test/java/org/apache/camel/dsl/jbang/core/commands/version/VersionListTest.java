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
import java.util.List;
import java.util.stream.Collectors;

import org.apache.camel.dsl.jbang.core.commands.CamelCommandBaseTestSupport;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.MavenResolverMixin;
import org.apache.camel.dsl.jbang.core.commands.QuarkusExtensionRegistryMixin;
import org.apache.camel.dsl.jbang.core.common.RuntimeType;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class VersionListTest extends CamelCommandBaseTestSupport {

    @Test
    void springbootVersionIsAvailable() throws Exception {
        VersionList versionList = new VersionList(new CamelJBangMain().withPrinter(printer));
        versionList.sort = "version";
        versionList.runtime = RuntimeType.springBoot;
        versionList.mavenResolver = new MavenResolverMixin();

        versionList.doCall();

        List<String> lines = printer.getLines();
        // normalize multiple spaces to single space to avoid failures due to column width changes
        String output = normalizeSpaces(lines.stream().collect(Collectors.joining("\n")));
        // there was a change where the information is stored in 4.15, thus the test on 4.14.1 and 4.15.0
        // lines.stream().forEach(System.out::println);
        Assertions.assertThat(output)
                .contains("4.14.1 3.5.6 17,21 LTS")
                .contains("4.15.0 3.5.6 17,21");
    }

    @Test
    void quarkusVersionIsAvailable() throws Exception {
        VersionList versionList = createQuarkusVersionList();

        versionList.doCall();

        List<String> lines = printer.getLines();
        // normalize multiple spaces to single space to avoid failures due to column width changes
        String output = normalizeSpaces(lines.stream().collect(Collectors.joining("\n")));
        // lines.stream().forEach(System.out::println);
        Assertions.assertThat(output)
                .doesNotContain("3.11.0 2.1.4.Final 11") // Camel versions 4+ by default
                .contains("4.14.0 3.27.0 17,21 LTS")
                .doesNotContain("3.27.0.CR1 17,21 RC") // --rc is off by default
        ;

    }

    @Test
    void quarkusRc() throws Exception {
        VersionList versionList = createQuarkusVersionList();
        versionList.rc = true;

        versionList.doCall();

        List<String> lines = printer.getLines();
        // normalize multiple spaces to single space to avoid failures due to column width changes
        String output = normalizeSpaces(lines.stream().collect(Collectors.joining("\n")));
        Assertions.assertThat(output)
                .contains("4.14.0 3.27.0 17,21 LTS")
                .contains("3.27.0.CR1 17,21 RC") // --rc is on now
        ;

    }

    @Test
    void quarkusLts() throws Exception {
        VersionList versionList = createQuarkusVersionList();
        versionList.lts = true;

        versionList.doCall();

        List<String> lines = printer.getLines();
        // normalize multiple spaces to single space to avoid failures due to column width changes
        String output = normalizeSpaces(lines.stream().collect(Collectors.joining("\n")));
        // lines.stream().forEach(System.out::println);
        Assertions.assertThat(output)
                .contains("4.14.0 3.27.0 17,21 LTS")
                .doesNotContain("3.27.0.CR1 17,21 RC")
                .doesNotContain("4.20.0 3.35.1 17,21 2026-04-27");

    }

    @Test
    void quarkusFromToVersion() throws Exception {
        VersionList versionList = createQuarkusVersionList();
        versionList.fromVersion = "3";
        versionList.toVersion = "3.12";

        versionList.doCall();

        List<String> lines = printer.getLines();
        // normalize multiple spaces to single space to avoid failures due to column width changes
        String output = normalizeSpaces(lines.stream().collect(Collectors.joining("\n")));
        Assertions.assertThat(output)
                .contains("3.11.0 2.1.4.Final 11")
                .doesNotContain("3.13.0 2.5.4.Final 11")
                .doesNotContain("4.14.0 3.27.0 17,21 LTS");
    }

    @Test
    void quarkusFromToDate() throws Exception {
        VersionList versionList = createQuarkusVersionList();
        versionList.fromDate = "2026-03-01";
        versionList.toDate = "2026-04-01";
        versionList.doCall();

        List<String> lines = printer.getLines();
        // normalize multiple spaces to single space to avoid failures due to column width changes
        String output = normalizeSpaces(lines.stream().collect(Collectors.joining("\n")));
        Assertions.assertThat(output)
                .contains("4.18.0 3.34.7 17,21 LTS 2026-03-25")
                .doesNotContain("4.17.0 3.31.1 17,21 2026-01-26")
                .doesNotContain("4.20.0 3.35.1 17,21 2026-04-27");

    }

    @Test
    void quarkusPatch() throws Exception {
        VersionList versionList = createQuarkusVersionList();
        versionList.patch = false;

        versionList.doCall();

        List<String> lines = printer.getLines();
        // normalize multiple spaces to single space to avoid failures due to column width changes
        String output = normalizeSpaces(lines.stream().collect(Collectors.joining("\n")));
        //lines.stream().forEach(System.out::println);
        Assertions.assertThat(output)
                .contains("4.9.0 3.18.4 17,21")
                .doesNotContain("4.10.8 3.20.6.1 17,21");
    }

    VersionList createQuarkusVersionList() {
        VersionList versionList = new VersionList(new CamelJBangMain().withPrinter(printer));
        versionList.sort = "version";
        versionList.runtime = RuntimeType.quarkus;
        versionList.mavenResolver = new MavenResolverMixin();
        versionList.quarkusExtensionRegistry
                = QuarkusExtensionRegistryMixin
                        .of(Path.of("target/test-classes/registry.quarkus.io").toAbsolutePath().normalize());
        return versionList;
    }

    private static String normalizeSpaces(String input) {
        return input.replaceAll("\\s+", " ");
    }

}
