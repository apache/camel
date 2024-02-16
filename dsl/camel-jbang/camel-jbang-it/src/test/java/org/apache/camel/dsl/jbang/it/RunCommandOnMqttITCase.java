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
package org.apache.camel.dsl.jbang.it;

import java.io.IOException;

import org.apache.camel.dsl.jbang.it.support.InVersion;
import org.apache.camel.dsl.jbang.it.support.JBangTestSupport;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.infra.mosquitto.services.MosquittoLocalContainerService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;

public class RunCommandOnMqttITCase extends JBangTestSupport {

    private static int mqttPort = AvailablePortFinder.getNextAvailable();
    private static MosquittoLocalContainerService service;

    @BeforeAll
    public static void init() {
        service = new MosquittoLocalContainerService(mqttPort);
        service.initialize();
    }

    @AfterAll
    public static void end() {
        service.shutdown();
    }

    @Test
    @InVersion(from = "4.00.00")
    public void sendMessageWithoutEndpoint() throws IOException {
        copyResourceInDataFolder(TestResources.MQQT_CONSUMER);
        final String ipAddr = getIpAddr(service.getContainer());
        final String pid = executeBackground(String.format("run --property=brokerUrl=%s %s/%s",
                "tcp://" + ipAddr + ":1883",
                mountPoint(), TestResources.MQQT_CONSUMER.getName()));
        checkLogContains("Started mqtt5-source");
        final String payloadFile = "payload.json";
        newFileInDataFolder(payloadFile, "{\"value\": 21}");
        sendCmd(String.format("%s/%s", mountPoint(), payloadFile), pid);
        checkLogContains("The temperature is 21");
    }

    private String getIpAddr(final GenericContainer container) {
        return container.getCurrentContainerInfo().getNetworkSettings().getNetworks().entrySet()
                .stream().filter(entry -> "127.0.0.1" != entry.getValue().getIpAddress())
                .map(entry -> entry.getValue().getIpAddress()).findFirst()
                .orElseThrow(() -> new IllegalStateException("no ip address found"));
    }

    private void sendCmd(String payloadFile, String pid) {
        execute(String.format("cmd send --body=file:%s %s", payloadFile, pid));
    }
}
