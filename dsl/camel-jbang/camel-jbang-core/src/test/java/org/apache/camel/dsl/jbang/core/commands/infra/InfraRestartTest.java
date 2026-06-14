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
package org.apache.camel.dsl.jbang.core.commands.infra;

import java.util.List;

import org.apache.camel.dsl.jbang.core.commands.CamelCommandBaseTestSupport;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

public class InfraRestartTest extends CamelCommandBaseTestSupport {

    /**
     * Restarting a service that is not currently running should simply start it. This also asserts that the restart
     * command delegates to the (foreground) infra run, reusing the same reflection path as {@link InfraTest}.
     */
    @Test
    public void restartService() throws Exception {
        // infraRestart.doCall() delegates to the blocking infra run, so execute it on a new thread.
        Thread thread = new Thread(() -> {
            InfraRestart infraRestart = new InfraRestart(new CamelJBangMain().withPrinter(printer));
            infraRestart.setServiceName(List.of("ftp"));
            try {
                infraRestart.doCall();
            } catch (Exception e) {
                printer.printErr(e);
                throw new RuntimeException(e);
            }
        });
        thread.start();

        Awaitility.await().untilAsserted(() -> {
            List<String> lines = printer.getLines();
            Assertions.assertThat(lines).anyMatch(l -> l.startsWith("Starting service ftp"));
            Assertions.assertThat(lines).contains("Running (use camel infra stop ftp to stop the execution)");
        });

        thread.interrupt();
    }
}
