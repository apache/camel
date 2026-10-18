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
package org.apache.camel.dsl.jbang.core.common;

import java.util.HashSet;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The logging of a command: configured once, as a command runs on its own, but a CLI that runs several commands in this
 * JVM (camel ask, camel shell, the monitor) gives each of them its own.
 */
class RuntimeUtilLoggingTest {

    @Test
    void newCommandOfACliThatRunsSeveralCommandsLogsAsOnItsOwn() throws Exception {
        RuntimeUtil.configureLog("off", false, false, false, false, null, null);
        Set<String> first = appenders();

        // a command run on its own keeps the configuration it started with
        RuntimeUtil.configureLog("info", false, false, false, true, null, null);
        assertThat(appenders()).isEqualTo(first);

        // the next command of camel ask or the monitor logs as it would on its own: export to its log file only
        RuntimeUtil.newCommand();
        RuntimeUtil.configureLog("info", false, false, false, true, null, null);
        assertThat(appenders()).containsExactly("file");

        // and the one after it logs to the console again
        RuntimeUtil.newCommand();
        RuntimeUtil.configureLog("off", false, false, false, false, null, null);
        assertThat(appenders()).contains("out");
    }

    private static Set<String> appenders() {
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        return new HashSet<>(context.getConfiguration().getAppenders().keySet());
    }
}
