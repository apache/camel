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

import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;
import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.RuntimeCompletionCandidates;
import org.apache.camel.dsl.jbang.core.common.RuntimeTypeConverter;
import org.apache.camel.dsl.jbang.run.common.RuntimeType;

@CommandDefinition(name = "set", description = "Set/change current Camel version", generateHelp = true,
                   footer = {
                           "Examples:",
                           "  camel version set 4.10.0",
                           "  camel version set --runtime=spring-boot 4.10.0",
                           "  camel version set --reset" })
public class VersionSet extends CamelCommand {

    @Argument(description = "Camel version")
    String version;

    @Option(name = "runtime", description = "Runtime (${COMPLETION-CANDIDATES})", completer = RuntimeCompletionCandidates.class,
            converter = RuntimeTypeConverter.class)
    RuntimeType runtime;

    @Option(name = "repo", aliases = { "repos" },
            description = "Maven repository for downloading the dependencies (Use commas to separate multiple repositories)")
    String repo;

    @Option(name = "reset", hasValue = false, description = "Reset by removing any custom version settings")
    boolean reset;

    @Option(name = "global", hasValue = false, description = "Use global or local configuration")
    boolean global = true;

    public VersionSet(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        boolean local = !global;
        CommandLineHelper.createPropertyFile(local);

        boolean ok = CommandLineHelper.loadProperties(properties -> {
            if (reset) {
                properties.remove("camel-version");
                properties.remove("kamelets-version");
                properties.remove("repos");
                properties.remove("runtime");
            } else {
                if (version != null) {
                    properties.put("camel-version", version);
                }
                if (repo != null) {
                    properties.put("repos", repo);
                }
                if (runtime != null) {
                    properties.put("runtime", runtime.runtime());
                }
            }
            CommandLineHelper.storeProperties(properties, printer(), local);
        }, local, printer());
        if (!ok) {
            printer().printErr(
                    "Cannot set version: config file is malformed. Edit it by hand, escaping '\\' as '\\\\' or using '/'.");
            return CliExitCode.FAILURE;
        }

        return 0;
    }

}
