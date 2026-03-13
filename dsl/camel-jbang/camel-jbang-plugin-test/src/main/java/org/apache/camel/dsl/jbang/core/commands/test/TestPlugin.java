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
package org.apache.camel.dsl.jbang.core.commands.test;

import java.util.Optional;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CamelJBangPlugin;
import org.apache.camel.dsl.jbang.core.common.Plugin;
import org.apache.camel.dsl.jbang.core.common.PluginExporter;
import picocli.CommandLine;

@CamelJBangPlugin(name = "camel-jbang-plugin-test", firstVersion = "4.14.0")
public class TestPlugin implements Plugin {

    @Override
    public void customize(CommandLine commandLine, CamelJBangMain main) {
        var cmd = new CommandLine(new TestCommand(main))
                .addSubcommand("init", new CommandLine(new TestInit(main)))
                .addSubcommand("run", new CommandLine(new TestRun(main)));

        commandLine.addSubcommand("test", cmd);
    }

    @Override
    public Optional<PluginExporter> getExporter() {
        return Optional.of(new TestPluginExporter());
    }
}
