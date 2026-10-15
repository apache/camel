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

import java.util.Optional;

import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.JsonOutput;
import org.apache.camel.util.json.JsonObject;
import picocli.CommandLine;

@CommandLine.Command(name = "get",
                     description = "Display user configuration value", sortOptions = false, showDefaultValues = true,
                     footer = {
                             "%nExamples:",
                             "  camel config get camel-version",
                             "  camel config get runtime",
                             "  camel config get runtime --json" })
public class ConfigGet extends CamelCommand {

    @CommandLine.Parameters(description = "Configuration key", arity = "1")
    String key;

    @CommandLine.Option(names = { "--global" }, description = "Use global or local configuration")
    boolean global = true;

    @CommandLine.Option(names = { "--json" }, description = "Output in JSON Format")
    boolean jsonOutput;

    public ConfigGet(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        Object[] value = { null };
        CommandLineHelper.loadProperties(properties -> value[0] = properties.get(key), !global);

        Optional<Object> maybeProperty = Optional.ofNullable(value[0]);
        if (maybeProperty.isEmpty()) {
            throw CliException.notFound(key + " key not found");
        }
        if (jsonOutput) {
            JsonObject jo = new JsonObject();
            jo.put("key", key);
            jo.put("value", String.valueOf(maybeProperty.get()));
            JsonOutput.print(printer(), jo);
        } else {
            printer().println(String.valueOf(maybeProperty.get()));
        }
        return 0;
    }
}
