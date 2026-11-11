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

import java.util.ArrayList;
import java.util.List;

import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Arguments;
import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;

/**
 * The test commands of Citrus. Citrus 5.1 defines its commands with picocli, so aesh does not parse this command line:
 * picocli parses and runs it with the commands of Citrus (see {@link TestPlugin#citrus(CamelJBangMain)}), until Citrus
 * is on aesh too.
 */
@CommandDefinition(name = "test", description = "Manage tests for Camel applications (use test --help to see sub commands)",
                   disableParsing = true)
public class TestCommand extends CamelCommand {

    @Arguments
    List<String> args = new ArrayList<>();

    public TestCommand(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        return TestPlugin.citrus(getMain()).execute(args.toArray(String[]::new));
    }
}
