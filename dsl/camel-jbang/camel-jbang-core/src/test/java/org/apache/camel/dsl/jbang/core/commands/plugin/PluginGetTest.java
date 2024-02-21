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

package org.apache.camel.dsl.jbang.core.commands.plugin;

import java.util.List;

import org.apache.camel.dsl.jbang.core.commands.CamelCommandBaseTest;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.PluginHelper;
import org.apache.camel.dsl.jbang.core.common.PluginType;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PluginGetTest extends CamelCommandBaseTest {

    @BeforeEach
    public void setup() {
        super.setup();

        CommandLineHelper.useHomeDir("target");
        PluginHelper.createPluginConfig();
    }

    @Test
    public void shouldGetEmptyPlugins() throws Exception {
        PluginGet command = new PluginGet(new CamelJBangMain().withPrinter(printer));
        command.doCall();

        Assertions.assertEquals("", printer.getOutput());
    }

    @Test
    public void shouldGetPlugin() throws Exception {
        PluginHelper.enable(PluginType.CAMEL_K);

        PluginGet command = new PluginGet(new CamelJBangMain().withPrinter(printer));
        command.doCall();

        List<String> output = printer.getLines();
        Assertions.assertEquals(2, output.size());
        Assertions.assertEquals("NAME     COMMAND  DEPENDENCY                             DESCRIPTION", output.get(0));
        Assertions.assertEquals(
                "camel-k  k        org.apache.camel:camel-jbang-plugin-k  %s".formatted(PluginType.CAMEL_K.getDescription()),
                output.get(1));
    }

    @Test
    public void shouldGetDefaultPlugins() throws Exception {
        PluginGet command = new PluginGet(new CamelJBangMain().withPrinter(printer));
        command.all = true;
        command.doCall();

        List<String> output = printer.getLines();
        Assertions.assertEquals(4, output.size());
        Assertions.assertEquals("Supported plugins:", output.get(0));
        Assertions.assertEquals("NAME     COMMAND  DEPENDENCY                             DESCRIPTION", output.get(2));
        Assertions.assertEquals(
                "camel-k  k        org.apache.camel:camel-jbang-plugin-k  %s".formatted(PluginType.CAMEL_K.getDescription()),
                output.get(3));
    }

    @Test
    public void shouldGenerateDependencyAndDescription() throws Exception {
        JsonObject pluginConfig = PluginHelper.getOrCreatePluginConfig();
        JsonObject plugins = pluginConfig.getMap("plugins");

        JsonObject fooPlugin = new JsonObject();
        fooPlugin.put("name", "foo");
        fooPlugin.put("command", "foo");
        plugins.put("foo-plugin", fooPlugin);

        PluginHelper.savePluginConfig(pluginConfig);

        PluginGet command = new PluginGet(new CamelJBangMain().withPrinter(printer));
        command.doCall();

        List<String> output = printer.getLines();
        Assertions.assertEquals(2, output.size());
        Assertions.assertEquals("NAME  COMMAND  DEPENDENCY                               DESCRIPTION", output.get(0));
        Assertions.assertEquals("foo   foo      org.apache.camel:camel-jbang-plugin-foo  Plugin foo called with command foo",
                output.get(1));
    }

    @Test
    public void shouldGetAllPlugins() throws Exception {
        JsonObject pluginConfig = PluginHelper.getOrCreatePluginConfig();
        JsonObject plugins = pluginConfig.getMap("plugins");

        JsonObject fooPlugin = new JsonObject();
        fooPlugin.put("name", "foo-plugin");
        fooPlugin.put("command", "foo");
        fooPlugin.put("dependency", "org.apache.camel:foo-plugin:1.0.0");
        plugins.put("foo-plugin", fooPlugin);

        PluginHelper.savePluginConfig(pluginConfig);

        PluginGet command = new PluginGet(new CamelJBangMain().withPrinter(printer));
        command.all = true;
        command.doCall();

        List<String> output = printer.getLines();
        Assertions.assertEquals(7, output.size());
        Assertions.assertEquals("NAME        COMMAND  DEPENDENCY                         DESCRIPTION", output.get(0));
        Assertions.assertEquals(
                "foo-plugin  foo      org.apache.camel:foo-plugin:1.0.0  Plugin foo-plugin called with command foo",
                output.get(1));

        Assertions.assertEquals("Supported plugins:", output.get(3));
        Assertions.assertEquals("NAME     COMMAND  DEPENDENCY                             DESCRIPTION", output.get(5));
        Assertions.assertEquals(
                "camel-k  k        org.apache.camel:camel-jbang-plugin-k  %s".formatted(PluginType.CAMEL_K.getDescription()),
                output.get(6));
    }

}
