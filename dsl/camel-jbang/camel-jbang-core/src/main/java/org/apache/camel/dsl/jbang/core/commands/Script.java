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

import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;
import org.aesh.command.option.OptionList;
import org.apache.camel.dsl.jbang.core.common.LoggingLevelCompletionCandidates;

@CommandDefinition(name = "script", description = "Run Camel integration as shell script for terminal scripting",
                   generateHelp = true,
                   footer = {
                           "Examples:",
                           "  camel script hello.java",
                           "  camel script hello.java --max-messages=10" })
public class Script extends CamelCommand {

    Path filePath; // Defined only for file path completion; the field never used
    @Argument(required = true, description = "Name of file", paramLabel = "<file>")
    String file;

    @Option(name = "max-messages", description = "Max number of messages to process before stopping", defaultValue = "0")
    int maxMessages;

    @Option(name = "max-seconds", description = "Max seconds to run before stopping", defaultValue = "0")
    int maxSeconds;

    @Option(name = "max-idle-seconds", description = "For how long time in seconds Camel can be idle before stopping",
            defaultValue = "1")
    int maxIdleSeconds;

    @Option(name = "logging", hasValue = false,
            description = "Can be used to turn on logging (logs to file in <user home>/.camel directory)",
            defaultValue = "false")
    boolean logging;

    @Option(name = "logging-level", description = "Logging level (${COMPLETION-CANDIDATES})", defaultValue = "info",
            completer = LoggingLevelCompletionCandidates.class)
    String loggingLevel;

    @Option(name = "properties", description = "Load properties file for route placeholders (ex. /path/to/file.properties")
    String propertiesFiles;

    @OptionList(name = "prop", aliases = { "property" }, valueSeparator = OptionList.NO_SEPARATOR,
                description = "Additional properties (override existing)")
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
        run.loggingOptions.logging = logging;
        run.loggingOptions.loggingLevel = loggingLevel;
        run.loggingOptions.loggingColor = false;
        run.executionLimitOptions.maxSeconds = maxSeconds;
        run.executionLimitOptions.maxMessages = maxMessages;
        run.executionLimitOptions.maxIdleSeconds = maxIdleSeconds;
        run.property = property;
        run.propertiesFiles = propertiesFiles;
        return run.runScript(file);
    }

}
