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

import java.util.function.BiFunction;

import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;
import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CamelVersionCheck;
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

    @Option(name = "reset", hasValue = false,
            description = "Reset by removing the version settings: camel-version, kamelets-version, repos and runtime")
    boolean reset;

    @Option(name = "global", hasValue = false, description = "Use global or local configuration")
    boolean global = true;

    /** Whether a version exists, given the version and the extra repositories: true, false, or null when unknown. */
    BiFunction<String, String, Boolean> versionCheck = CamelVersionCheck::exists;

    public VersionSet(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        boolean local = !global;
        if (!reset && version == null && repo == null && runtime == null) {
            printer().printErr("Give the Camel version to set, --runtime, --repo, or --reset");
            return CliExitCode.USAGE;
        }
        if (!reset && version != null) {
            // a version that was never released fails every command that uses it later (CAMEL-26931)
            String repos = repo;
            if (repos == null) {
                String[] stored = new String[1];
                CommandLineHelper.loadProperties(p -> stored[0] = p.getProperty("repos"), local, printer());
                repos = stored[0];
            }
            Boolean exists = versionCheck.apply(version, repos);
            if (Boolean.FALSE.equals(exists)) {
                printer().printErr("Camel version " + version + " not found (camel version list shows the released versions)");
                return CliExitCode.NOT_FOUND;
            } else if (exists == null) {
                printer().printDiagnostic("Cannot check that Camel version " + version
                                          + " exists: the Maven repositories cannot be reached");
            }
        }
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

        return CliExitCode.OK;
    }

}
