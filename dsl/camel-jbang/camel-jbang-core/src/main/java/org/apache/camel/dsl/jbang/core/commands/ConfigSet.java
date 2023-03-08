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
package org.apache.camel.dsl.jbang.core.commands;

import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import picocli.CommandLine;

@CommandLine.Command(name = "set",
                     description = "Set user config value")
public class ConfigSet extends CamelCommand {

    @CommandLine.Parameters(description = "Configuration parameter (ex. key=value)")
    private String configuration;

    public ConfigSet(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer call() throws Exception {
        CommandLineHelper.createPropertyFile();

        if (configuration.split("=").length == 1) {
            System.out.println("Configuration parameter not in key=value form");

            return 1;
        }

        CommandLineHelper.loadProperties(properties -> {
            String key = configuration.substring(0, configuration.indexOf("="));
            String value = configuration.substring(configuration.indexOf("=") + 1, configuration.length());
            properties.put(key, value);
            CommandLineHelper.storeProperties(properties);
        });

        return 0;
    }
}
