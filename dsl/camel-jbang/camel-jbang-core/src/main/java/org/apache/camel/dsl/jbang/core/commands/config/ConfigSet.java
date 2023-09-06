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

import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.util.StringHelper;
import picocli.CommandLine;

@CommandLine.Command(name = "set",
                     description = "Set user configuration value", sortOptions = false)
public class ConfigSet extends CamelCommand {

    @CommandLine.Parameters(description = "Configuration parameter (ex. key=value)", arity = "1")
    private String configuration;

    public ConfigSet(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        CommandLineHelper.createPropertyFile();

        if (configuration.split("=").length == 1) {
            System.out.println("Configuration parameter not in key=value format");
            return 1;
        }

        CommandLineHelper.loadProperties(properties -> {
            String key = StringHelper.before(configuration, "=").trim();
            String value = StringHelper.after(configuration, "=").trim();
            properties.put(key, value);
            CommandLineHelper.storeProperties(properties);
        });

        return 0;
    }
}
