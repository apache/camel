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
import org.citrusframework.jbang.JBangSettings;
import org.citrusframework.jbang.JBangSupport;
import org.citrusframework.jbang.ProcessAndOutput;
import picocli.CommandLine;

@CamelJBangPlugin(name = "camel-jbang-plugin-test", firstVersion = "4.14.0")
public class TestPlugin implements Plugin {

    @Override
    public void customize(CommandLine commandLine, CamelJBangMain main) {
        commandLine.setExecutionStrategy(new CitrusExecutionStrategy(main))
                .addSubcommand("test", new CommandLine(new TestCommand(main))
                        .setUnmatchedArgumentsAllowed(true)
                        .setUnmatchedOptionsAllowedAsOptionParameters(true));
    }

    @Override
    public Optional<PluginExporter> getExporter() {
        return Optional.of(new TestPluginExporter());
    }

    /**
     * Command execution strategy delegates to Citrus JBang for subcommands like init or run.
     *
     * @param main Camel JBang main that provides the output printer.
     */
    private record CitrusExecutionStrategy(CamelJBangMain main) implements CommandLine.IExecutionStrategy {

        @Override
        public int execute(CommandLine.ParseResult parseResult)
                throws CommandLine.ExecutionException, CommandLine.ParameterException {
            ProcessAndOutput pao;
            if (parseResult.originalArgs().size() > 2) {
                pao = JBangSupport.jbang().app(JBangSettings.getApp())
                        .run(parseResult.originalArgs().get(1),
                                parseResult.originalArgs().subList(2, parseResult.originalArgs().size()));
            } else if (parseResult.originalArgs().size() == 2) {
                pao = JBangSupport.jbang().app(JBangSettings.getApp()).run(parseResult.originalArgs().get(1));
            } else {
                // run help command by default
                pao = JBangSupport.jbang().app(JBangSettings.getApp()).run("-h");
            }

            main.getOut().print(pao.getOutput());
            return pao.getProcess().exitValue();
        }
    }
}
