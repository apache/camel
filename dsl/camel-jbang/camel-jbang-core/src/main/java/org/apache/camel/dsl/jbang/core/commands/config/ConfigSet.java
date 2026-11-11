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

import java.io.IOException;

import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;
import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.util.StringHelper;

@CommandDefinition(name = "set", description = "Set user configuration value", generateHelp = true,
                   footer = {
                           "Examples:",
                           "  camel config set camel-version=4.10.0",
                           "  camel config set runtime=spring-boot" })
public class ConfigSet extends CamelCommand {

    @Argument(required = true, description = "Configuration parameter (ex. key=value)")
    String configuration;

    @Option(name = "global", hasValue = false, description = "Use global or local configuration")
    boolean global = true;

    public ConfigSet(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        return setConfiguration(!global);
    }

    private int setConfiguration(boolean local) throws IOException {
        String name = StringHelper.before(configuration, "=");
        if (name == null || name.isBlank() || configuration.split("=").length == 1) {
            throw CliException.usage("Configuration parameter not in key=value format: " + configuration);
        }
        CommandLineHelper.createPropertyFile(local);

        CommandLineHelper.loadProperties(properties -> {
            String key = StringHelper.before(configuration, "=").trim();
            String value = StringHelper.after(configuration, "=").trim();
            properties.put(key, value);
            CommandLineHelper.storeProperties(properties, printer(), local);
        }, local);

        return 0;
    }
}
