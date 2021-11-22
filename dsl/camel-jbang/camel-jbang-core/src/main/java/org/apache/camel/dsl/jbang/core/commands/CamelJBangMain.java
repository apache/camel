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

import java.util.concurrent.Callable;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "CamelJBang", mixinStandardHelpOptions = true, version = "CamelJBang",
         description = "A JBang-based Camel app")
public class CamelJBangMain implements Callable<Integer> {
    private static CommandLine commandLine;

    public static void run(String... args) {
        commandLine = new CommandLine(new CamelJBangMain())
                .addSubcommand("run", new Run())
                .addSubcommand("search", new CommandLine(new Search())
                        .addSubcommand("kamelets", new SearchKamelets())
                        .addSubcommand("components", new SearchComponents())
                        .addSubcommand("languages", new SearchLanguages())
                        .addSubcommand("others", new SearchOthers()))
                .addSubcommand("init", new CommandLine(new Init())
                        .addSubcommand("kamelet", new InitKamelet())
                        .addSubcommand("binding", new InitBinding()));

        int exitCode = commandLine.execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        commandLine.execute("--help");
        return 0;
    }
}
