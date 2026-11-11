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
package org.apache.camel.dsl.jbang.core.commands.kubernetes;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CamelJBangPlugin;
import org.apache.camel.dsl.jbang.core.common.CommandTree;
import org.apache.camel.dsl.jbang.core.common.Plugin;

@CamelJBangPlugin(name = "camel-jbang-plugin-kubernetes", firstVersion = "4.8.0")
public class KubernetesPlugin implements Plugin {

    @Override
    public void customize(CommandTree commandLine, CamelJBangMain main) {
        var cmd = new CommandTree(new KubernetesCommand(main))
                .addSubcommand("run", new CommandTree(new KubernetesRun(main)))
                .addSubcommand("export", new CommandTree(new KubernetesExport(main)))
                .addSubcommand("delete", new CommandTree(new KubernetesDelete(main)))
                .addSubcommand("logs", new CommandTree(new KubernetesPodLogs(main)));

        commandLine.addSubcommand("kubernetes", cmd, "k8s");
    }
}
