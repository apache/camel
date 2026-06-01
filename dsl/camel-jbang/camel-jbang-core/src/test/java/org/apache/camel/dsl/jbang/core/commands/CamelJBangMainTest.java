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

import java.lang.reflect.Field;
import java.util.List;

import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.PluginHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CamelJBangMainTest extends CamelCommandBaseTestSupport {

    private CamelJBangMain main;

    @BeforeEach
    public void setup() throws Exception {
        super.setup();

        CommandLineHelper.useHomeDir("target");
        PluginHelper.createPluginConfig();

        main = new CamelJBangMain().withPrinter(printer);

        // set the static commandLine field so printAvailablePlugins() can check subcommands
        Field f = CamelJBangMain.class.getDeclaredField("commandLine");
        f.setAccessible(true);
        f.set(null, new CommandLine(main));
    }

    @Test
    public void shouldShowPluginBannerByDefault() throws Exception {
        UserConfigHelper.createUserConfig("");

        main.printAvailablePlugins();

        List<String> output = printer.getLines();
        assertTrue(output.stream().anyMatch(l -> l.contains("Plugins (not installed):")));
        assertTrue(output.stream().anyMatch(l -> l.contains("Turn off: camel config set camel.jbang.plugin.banner=false")));
    }

    @Test
    public void shouldHidePluginBannerWhenDisabled() throws Exception {
        UserConfigHelper.createUserConfig("camel.jbang.plugin.banner=false");

        main.printAvailablePlugins();

        List<String> output = printer.getLines();
        assertFalse(output.stream().anyMatch(l -> l.contains("Plugins (not installed):")));
    }
}
