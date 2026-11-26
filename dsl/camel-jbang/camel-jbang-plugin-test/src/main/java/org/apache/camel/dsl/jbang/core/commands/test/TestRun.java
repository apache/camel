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

import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.invocation.CommandInvocation;
import org.apache.camel.dsl.jbang.run.common.RuntimeUtil;
import org.citrusframework.jbang.cli.CitrusJBangMain;
import org.citrusframework.jbang.cli.commands.Run;

/**
 * Adjusts the thread context class loader and configures logging for the duration of the Citrus test run so that Citrus
 * can resolve its resources.
 */
public class TestRun extends Run {

    public TestRun(CitrusJBangMain citrus) {
        super(citrus);
    }

    @Override
    public CommandResult execute(CommandInvocation invocation) throws CommandException, InterruptedException {
        return TestPlugin.withClassLoader(inv -> {
            configureLogging();
            return super.execute(inv);
        }, invocation);
    }

    /**
     * Reconfigure the existing Log4j2 context so that Citrus SLF4J log output appears on the console. Citrus's
     * LoggingSupport creates a separate named context that the SLF4J loggers do not use.
     */
    private void configureLogging() {
        try {
            boolean logging = TestPlugin.readField(Run.class, "logging", this);
            boolean loggingColor = TestPlugin.readField(Run.class, "loggingColor", this);
            String loggingLevel = TestPlugin.readField(Run.class, "loggingLevel", this);

            if (logging) {
                RuntimeUtil.configureLog(loggingLevel, loggingColor, false, false, false, null, null);
            } else {
                RuntimeUtil.configureLog("off", false, false, false, false, null, null);
            }
        } catch (Exception e) {
            printer().printErr("Failed to configure logging for run command '%s': %s"
                    .formatted(e.getClass().getName(), e.getMessage()));
        }
    }
}
