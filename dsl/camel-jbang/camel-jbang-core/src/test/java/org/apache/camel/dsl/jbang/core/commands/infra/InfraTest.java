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
import java.util.stream.Collectors;

import org.apache.camel.dsl.jbang.core.commands.CamelCommandBaseTest;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

public class InfraTest extends CamelCommandBaseTest {

    /**
     * This test asserts that the reflection in InfraRun works as expected. In case of issues in the reflection, most
     * probably the affected module is camel-test-infra-common
     */
    @Test
    public void runService() throws Exception {
        // infraRun.doCall() is blocking, run on a new thread.
        Thread thread = new Thread(() -> {
            InfraRun infraRun = new InfraRun(new CamelJBangMain().withPrinter(printer));
            infraRun.setServiceName(List.of("ftp"));
            try {
                infraRun.doCall();
            } catch (Exception e) {
                printer.printErr(e);
                throw new RuntimeException(e);
            }
        });
        thread.start();

        Awaitility.await().untilAsserted(() -> {
            List<String> lines = printer.getLines();
            Assertions.assertThat(lines).contains("Starting service ftp");
            Assertions.assertThat(lines).contains("Press any key to stop the execution");
        });

        thread.interrupt();
    }

    @Test
    public void listServices() throws Exception {
        InfraList infraList = new InfraList(new CamelJBangMain().withPrinter(printer));

        infraList.doCall();

        List<String> lines = printer.getLines();
        String output = lines.stream().collect(Collectors.joining(" "));
        Assertions.assertThat(output).contains("ALIAS");
        Assertions.assertThat(output).contains("artemis");
        Assertions.assertThat(output).contains("amqp");
        Assertions.assertThat(output).contains("minio");
    }
}
