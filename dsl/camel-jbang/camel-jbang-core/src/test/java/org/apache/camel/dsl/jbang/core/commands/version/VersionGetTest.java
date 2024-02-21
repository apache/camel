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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;

import org.apache.camel.dsl.jbang.core.commands.CamelCommandBaseTest;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.UserConfigHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class VersionGetTest extends CamelCommandBaseTest {

    @Test
    public void shouldPrintVersions() throws Exception {
        UserConfigHelper.createUserConfig("");
        createJBangVersionFile("0.100");

        VersionGet command = createCommand();
        command.doCall();

        List<String> lines = printer.getLines();
        Assertions.assertEquals("JBang version: 0.100", lines.get(0));
        Assertions.assertTrue(lines.get(1).startsWith("Camel JBang version:"));
    }

    @Test
    public void shouldPrintUserProperties() throws Exception {
        UserConfigHelper.createUserConfig("""
                camel-version=latest
                foo=bar
                kamelets-version=greatest
                """);
        createJBangVersionFile("0.101");

        VersionGet command = createCommand();
        command.doCall();

        List<String> lines = printer.getLines();
        Assertions.assertEquals(5, lines.size());
        Assertions.assertEquals("JBang version: 0.101", lines.get(0));
        Assertions.assertTrue(lines.get(1).startsWith("Camel JBang version:"));
        Assertions.assertEquals("User configuration:", lines.get(2));
        Assertions.assertEquals("camel-version = latest", lines.get(3));
        Assertions.assertEquals("kamelets-version = greatest", lines.get(4));
    }

    private void createJBangVersionFile(String version) throws IOException {
        Path jBangDir = Paths.get("target", ".jbang", "cache");
        if (!jBangDir.toFile().exists()) {
            jBangDir.toFile().mkdirs();
        }

        Files.writeString(jBangDir.resolve("version.txt"), version,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private VersionGet createCommand() {
        return new VersionGet(new CamelJBangMain().withPrinter(printer));
    }

}
