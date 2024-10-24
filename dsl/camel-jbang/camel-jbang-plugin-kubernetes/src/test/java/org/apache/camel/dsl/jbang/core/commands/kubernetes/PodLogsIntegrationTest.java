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

package org.apache.camel.dsl.jbang.core.commands.kubernetes;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.Printer.SystemOutPrinter;
import org.junit.jupiter.api.Test;

class PodLogsIntegrationTest {

    private final CamelJBangMain jbangMain = new CamelJBangMain()
            .withPrinter(new SystemOutPrinter());

    @Test
    public void shouldShowPodLogs() {
        try {
            var podLogs = new PodLogs(jbangMain);
            podLogs.withClient(KubernetesHelper.getKubernetesClient());
            podLogs.name = "timer-log";
            podLogs.doCall();
        } catch (Exception e) {
            var printer = jbangMain.getOut();
            printer.println("Cannot access pod logs: " + e);
        }
    }
}
