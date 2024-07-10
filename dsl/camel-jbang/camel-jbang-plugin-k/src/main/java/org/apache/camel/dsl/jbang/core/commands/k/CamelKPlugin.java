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
package org.apache.camel.dsl.jbang.core.commands.k;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CamelJBangPlugin;
import org.apache.camel.dsl.jbang.core.common.Plugin;
import picocli.CommandLine;

@CamelJBangPlugin(name = "camel-jbang-plugin-k", firstVersion = "4.4.0")
public class CamelKPlugin implements Plugin {

    @Override
    public void customize(CommandLine commandLine, CamelJBangMain main) {
        var cmd = new picocli.CommandLine(new CamelKCommand(main))
                .addSubcommand(Agent.ID, new picocli.CommandLine(new Agent(main)))
                .addSubcommand("get", new picocli.CommandLine(new IntegrationGet(main)))
                .addSubcommand("run", new picocli.CommandLine(new IntegrationRun(main)))
                .addSubcommand("bind", new picocli.CommandLine(new Bind(main)))
                .addSubcommand("delete", new picocli.CommandLine(new IntegrationDelete(main)))
                .addSubcommand("logs", new picocli.CommandLine(new IntegrationLogs(main)));

        commandLine.addSubcommand("k", cmd);
    }
}
