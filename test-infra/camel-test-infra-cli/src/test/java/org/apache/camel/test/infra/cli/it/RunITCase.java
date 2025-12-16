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
package org.apache.camel.test.infra.cli.it;

import org.apache.camel.support.ObjectHelper;
import org.apache.camel.test.infra.cli.services.CliService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junitpioneer.jupiter.ReadsSystemProperty;
import org.junitpioneer.jupiter.RestoreSystemProperties;
import org.junitpioneer.jupiter.SetSystemProperty;

@RestoreSystemProperties
public class RunITCase extends AbstractTestSupport {

    @Test
    @ReadsSystemProperty
    @EnabledIfSystemProperty(named = "currentProjectVersion", matches = "^(?!\\s*$).+",
                             disabledReason = "currentProjectVersion system property must be set")
    public void readPidFromBackgroundExecutionInCurrentVersionTest() {
        String currentCamelVersion = System.getProperty("currentProjectVersion");
        System.setProperty("cli.service.version", currentCamelVersion);
        execute(this::checkPidFromBackgroundExec);
        System.clearProperty("cli.service.version");
    }

    @Test
    @SetSystemProperty(key = "cli.service.version", value = "4.14.2")
    public void readPidFromBackgroundExecutionInPreviousVersionTest() {
        execute(this::checkPidFromBackgroundExec);
    }

    private void checkPidFromBackgroundExec(CliService cliService) {
        cliService.execute("init foo.yaml");
        String pid = cliService.executeBackground("run foo.yaml");
        Assertions.assertTrue(ObjectHelper.isNumber(pid), "pid is numeric: " + pid);
    }
}
