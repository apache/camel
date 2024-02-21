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

import java.nio.file.Path;
import java.util.Stack;

import org.apache.camel.dsl.jbang.core.common.LoggingLevelCompletionCandidates;
import picocli.CommandLine;

@CommandLine.Command(name = "script", description = "Run Camel integration as shell script for terminal scripting",
                     sortOptions = false)
public class Script extends CamelCommand {

    @CommandLine.Parameters(description = "Name of file", arity = "1",
                            paramLabel = "<file>", parameterConsumer = FileConsumer.class)
    Path filePath; // Defined only for file path completion; the field never used
    String file;

    @CommandLine.Option(names = { "--max-messages" }, defaultValue = "0",
                        description = "Max number of messages to process before stopping")
    int maxMessages;

    @CommandLine.Option(names = { "--max-seconds" }, defaultValue = "0", description = "Max seconds to run before stopping")
    int maxSeconds;

    @CommandLine.Option(names = { "--max-idle-seconds" }, defaultValue = "1",
                        description = "For how long time in seconds Camel can be idle before stopping")
    int maxIdleSeconds;

    @CommandLine.Option(names = { "--logging" }, defaultValue = "false",
                        description = "Can be used to turn on logging (logs to file in <user home>/.camel directory)")
    boolean logging;

    @CommandLine.Option(names = { "--logging-level" }, completionCandidates = LoggingLevelCompletionCandidates.class,
                        defaultValue = "info", description = "Logging level")
    String loggingLevel;

    @CommandLine.Option(names = { "--properties" },
                        description = "Load properties file for route placeholders (ex. /path/to/file.properties")
    String propertiesFiles;

    @CommandLine.Option(names = { "-p", "--prop", "--property" }, description = "Additional properties (override existing)",
                        arity = "0")
    String[] property;

    public Script(CamelJBangMain main) {
        super(main);
    }

    @Override
    public boolean disarrangeLogging() {
        return false;
    }

    @Override
    public Integer doCall() throws Exception {
        // remove leading ./ when calling a script in script mode
        if (file != null && file.startsWith("./")) {
            file = file.substring(2);
        }

        Run run = new Run(getMain());
        run.logging = logging;
        run.loggingLevel = loggingLevel;
        run.loggingColor = false;
        run.maxSeconds = maxSeconds;
        run.maxMessages = maxMessages;
        run.maxIdleSeconds = maxIdleSeconds;
        run.property = property;
        run.propertiesFiles = propertiesFiles;
        return run.runScript(file);
    }

    static class FileConsumer extends ParameterConsumer<Script> {
        @Override
        protected void doConsumeParameters(Stack<String> args, Script cmd) {
            cmd.file = args.pop();
        }
    }

}
