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

package org.apache.camel.dsl.jbang.core.commands.config;

import java.util.List;

import org.apache.camel.dsl.jbang.core.commands.CamelCommandBaseTest;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.UserConfigHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ConfigListTest extends CamelCommandBaseTest {

    @Test
    public void shouldHandleEmptyConfig() throws Exception {
        UserConfigHelper.createUserConfig("");

        ConfigList command = new ConfigList(new CamelJBangMain().withPrinter(printer));
        command.doCall();

        Assertions.assertEquals("", printer.getOutput());
    }

    @Test
    public void shouldListUserConfig() throws Exception {
        UserConfigHelper.createUserConfig("""
                camel-version=latest
                kamelets-version=greatest
                foo=bar
                """);

        ConfigList command = new ConfigList(new CamelJBangMain().withPrinter(printer));
        command.doCall();

        List<String> lines = printer.getLines();
        Assertions.assertEquals(3, lines.size());
        Assertions.assertEquals("camel-version = latest", lines.get(0));
        Assertions.assertEquals("kamelets-version = greatest", lines.get(1));
        Assertions.assertEquals("foo = bar", lines.get(2));
    }

}
