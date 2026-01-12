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

import java.util.List;
import java.util.stream.Collectors;

import org.apache.camel.dsl.jbang.core.commands.CamelCommandBaseTestSupport;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.RuntimeType;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class VersionListTest extends CamelCommandBaseTestSupport {

    @Test
    void springbootVersionIsAvailable() throws Exception {
        VersionList versionList = new VersionList(new CamelJBangMain().withPrinter(printer));
        versionList.sort = "version";
        versionList.runtime = RuntimeType.springBoot;

        versionList.doCall();

        List<String> lines = printer.getLines();
        // normalize multiple spaces to single space to avoid failures due to column width changes
        String output = normalizeSpaces(lines.stream().collect(Collectors.joining("\n")));
        // there was a change where the information is stored in 4.15, thus the test on 4.14.1 and 4.15.0
        Assertions.assertThat(output)
                .contains("4.14.1 3.5.6 17,21 LTS")
                .contains("4.15.0 3.5.6 17,21");
    }

    @Test
    void quarkusVersionIsAvailable() throws Exception {
        VersionList versionList = new VersionList(new CamelJBangMain().withPrinter(printer));
        versionList.sort = "version";
        versionList.runtime = RuntimeType.quarkus;

        versionList.doCall();

        List<String> lines = printer.getLines();
        // normalize multiple spaces to single space to avoid failures due to column width changes
        String output = normalizeSpaces(lines.stream().collect(Collectors.joining("\n")));
        Assertions.assertThat(output)
                .contains("4.14.0 3.27.0 17,21 LTS");
    }

    private static String normalizeSpaces(String input) {
        return input.replaceAll("\\s+", " ");
    }

}
