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
package org.apache.camel.dsl.jbang.core.commands.config;

import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;
import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;

@CommandDefinition(name = "unset", description = "Remove user configuration value", generateHelp = true,
                   footer = {
                           "Examples:",
                           "  camel config unset myKey" })
public class ConfigUnset extends CamelCommand {

    @Argument(required = true, description = "Configuration key")
    String key;

    @Option(name = "global", hasValue = false, description = "Use global or local configurations")
    boolean global = true;

    public ConfigUnset(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        if (!CommandLineHelper.loadProperties(properties -> {
            properties.remove(key);
            CommandLineHelper.storeProperties(properties, printer(), !global);
        }, !global, printer())) {
            printer().printErr("Cannot unset configuration: the configuration file is malformed. Fix it by hand first.");
            return CliExitCode.FAILURE;
        }

        return CliExitCode.OK;
    }
}
