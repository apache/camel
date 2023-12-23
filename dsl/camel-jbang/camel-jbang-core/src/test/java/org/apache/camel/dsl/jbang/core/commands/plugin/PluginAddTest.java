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

import org.apache.camel.dsl.jbang.core.commands.CamelCommandBaseTest;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.PluginHelper;
import org.apache.camel.dsl.jbang.core.common.PluginType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PluginAddTest extends CamelCommandBaseTest {

    @BeforeEach
    public void setup() {
        super.setup();

        CommandLineHelper.useHomeDir("target");
        PluginHelper.createPluginConfig();
    }

    @Test
    public void shouldAddDefaultPlugin() throws Exception {
        PluginAdd command = new PluginAdd(new CamelJBangMain().withPrinter(printer));
        command.name = "camel-k";
        command.doCall();

        Assertions.assertEquals("", printer.getOutput());

        Assertions.assertEquals("{\"plugins\":{\"camel-k\":{\"name\":\"camel-k\",\"command\":\"k\",\"description\":\"%s\"}}}"
                .formatted(PluginType.CAMEL_K.getDescription()), PluginHelper.getOrCreatePluginConfig().toJson());
    }

    @Test
    public void shouldAddPlugin() throws Exception {
        PluginAdd command = new PluginAdd(new CamelJBangMain().withPrinter(printer));
        command.name = "foo-plugin";
        command.command = "foo";
        command.description = "Some plugin";
        command.doCall();

        Assertions.assertEquals("", printer.getOutput());

        Assertions.assertEquals("{\"plugins\":{\"foo-plugin\":{\"name\":\"foo-plugin\",\"command\":\"foo\"," +
                                "\"description\":\"Some plugin\"}}}",
                PluginHelper.getOrCreatePluginConfig().toJson());
    }

    @Test
    public void shouldGenerateProperties() throws Exception {
        PluginAdd command = new PluginAdd(new CamelJBangMain().withPrinter(printer));
        command.name = "foo";
        command.doCall();

        Assertions.assertEquals("", printer.getOutput());

        Assertions.assertEquals("{\"plugins\":{\"foo\":{\"name\":\"foo\",\"command\":\"foo\"," +
                                "\"description\":\"Plugin foo called with command foo\"}}}",
                PluginHelper.getOrCreatePluginConfig().toJson());
    }

    @Test
    public void shouldUseMavenGAV() throws Exception {
        PluginAdd command = new PluginAdd(new CamelJBangMain().withPrinter(printer));
        command.name = "foo-plugin";
        command.command = "foo";
        command.gav = "org.apache.camel:foo-plugin:1.0.0";
        command.doCall();

        Assertions.assertEquals("", printer.getOutput());

        Assertions.assertEquals("{\"plugins\":{\"foo-plugin\":{\"name\":\"foo-plugin\",\"command\":\"foo\"," +
                                "\"description\":\"Plugin foo-plugin called with command foo\",\"dependency\":\"org.apache.camel:foo-plugin:1.0.0\"}}}",
                PluginHelper.getOrCreatePluginConfig().toJson());
    }

    @Test
    public void shouldUseArtifactIdAndVersion() throws Exception {
        PluginAdd command = new PluginAdd(new CamelJBangMain().withPrinter(printer));
        command.name = "foo-plugin";
        command.command = "foo";
        command.groupId = "org.foo";
        command.artifactId = "foo-bar";
        command.version = "1.0.0";
        command.doCall();

        Assertions.assertEquals("", printer.getOutput());

        Assertions.assertEquals("{\"plugins\":{\"foo-plugin\":{\"name\":\"foo-plugin\",\"command\":\"foo\"," +
                                "\"description\":\"Plugin foo-plugin called with command foo\",\"dependency\":\"org.foo:foo-bar:1.0.0\"}}}",
                PluginHelper.getOrCreatePluginConfig().toJson());
    }

}
