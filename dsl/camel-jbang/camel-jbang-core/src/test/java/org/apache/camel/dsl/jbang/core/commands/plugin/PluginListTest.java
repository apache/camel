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

import org.apache.camel.dsl.jbang.core.commands.CamelCommandBaseTestSupport;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.PluginHelper;
import org.apache.camel.dsl.jbang.core.common.PluginType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PluginListTest extends CamelCommandBaseTestSupport {

    @BeforeEach
    public void setup() throws Exception {
        super.setup();

        CommandLineHelper.useHomeDir("target");
        PluginHelper.createPluginConfig();
    }

    @Test
    public void shouldListDefaultPlugins() throws Exception {
        PluginList command = new PluginList(new CamelJBangMain().withPrinter(printer));
        command.doCall();

        List<String> output = printer.getLines();
        Assertions.assertTrue(output.size() > 2);
        Assertions.assertEquals("Bundled plugins:", output.get(0));
    }

    @Test
    public void shouldListAllPlugins() throws Exception {
        PluginHelper.enable(PluginType.KUBERNETES);

        PluginList command = new PluginList(new CamelJBangMain().withPrinter(printer));
        command.doCall();

        List<String> output = printer.getLines();
        Assertions.assertTrue(output.size() > 2);
        Assertions.assertEquals("Installed plugins:", output.get(0));

        boolean hasBundled = output.stream().anyMatch(l -> l.equals("Bundled plugins:"));
        Assertions.assertTrue(hasBundled);
    }

}
