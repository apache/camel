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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InfraSupportTest {

    @TempDir
    Path camelDir;

    private static InfraInfo mosquitto() {
        InfraInfo info = new InfraInfo();
        info.alias = "mosquitto";
        info.pid = "8903";
        info.alive = true;
        info.description = "Mosquitto is a message broker that implements MQTT protocol";
        info.serviceVersion = "2.0.22";
        info.properties.put("brokerUrl", "tcp://localhost:1883");
        info.properties.put("description", info.description);
        info.properties.put("serviceVersion", info.serviceVersion);
        info.properties.put("getPort", 1883);
        info.properties.put("port", 1883);
        return info;
    }

    @Test
    void findMatchesAliasCaseInsensitivelyOrPidAndSkipsVanished() {
        InfraInfo mosquitto = mosquitto();
        InfraInfo gone = new InfraInfo();
        gone.alias = "kafka";
        gone.pid = "1";
        gone.vanishing = true;
        List<InfraInfo> services = List.of(mosquitto, gone);

        assertSame(mosquitto, InfraSupport.find(services, "Mosquitto"));
        assertSame(mosquitto, InfraSupport.find(services, "8903"));
        assertNull(InfraSupport.find(services, "kafka"));
        assertNull(InfraSupport.find(services, ""));
        assertNull(InfraSupport.find(null, "mosquitto"));
    }

    @Test
    void toJsonExposesConnectionPropertiesWithoutDuplicatingTopLevelFields() {
        JsonObject json = InfraSupport.toJson(mosquitto());

        assertEquals("mosquitto", json.get("alias"));
        assertEquals("8903", json.get("pid"));
        assertEquals("2.0.22", json.get("version"));
        assertEquals(true, json.get("alive"));
        JsonObject props = (JsonObject) json.get("properties");
        assertEquals("tcp://localhost:1883", props.get("brokerUrl"));
        assertEquals(1883, props.get("port"));
        assertFalse(props.containsKey("description"));
        assertFalse(props.containsKey("serviceVersion"));
        assertFalse(props.containsKey("getPort"));
    }

    @Test
    void readLogTailReturnsNewestFirstHonoursLimitAndFilter() throws Exception {
        InfraInfo info = mosquitto();
        Files.writeString(InfraSupport.logFile(camelDir, info), """
                1: mosquitto version 2.0.22 starting
                2: Opening ipv4 listen socket on port 1883.

                3: New connection from 192.168.64.1:32148 on port 1883.
                4: New client connected as mqtt-source
                """);

        List<String> all = InfraSupport.readLogTail(camelDir, info, 50, null);
        assertEquals(4, all.size(), "blank lines are skipped");
        assertTrue(all.get(0).startsWith("4:"), "newest first");
        assertTrue(all.get(3).startsWith("1:"));

        assertEquals(2, InfraSupport.readLogTail(camelDir, info, 2, null).size());

        List<String> filtered = InfraSupport.readLogTail(camelDir, info, 50, "PORT 1883");
        assertEquals(2, filtered.size());
        assertTrue(filtered.get(0).startsWith("3:"));
    }

    @Test
    void readLogTailIsEmptyWhenServiceHasNoLogYet() throws Exception {
        assertTrue(InfraSupport.readLogTail(camelDir, mosquitto(), 50, null).isEmpty());
    }

    @Test
    void stopRemovesPidFileAndReportsWhenNoProcessIsRunning() throws Exception {
        InfraInfo info = mosquitto();
        // a pid no live process can have, so the TUI never terminates a real process from this test
        info.pid = String.valueOf(Long.MAX_VALUE);
        Path pidFile = camelDir.resolve("infra-mosquitto-" + info.pid + ".json");
        Files.writeString(pidFile, "{}");

        assertFalse(InfraSupport.stop(camelDir, info));
        assertFalse(Files.exists(pidFile), "pid file is removed so the TUI stops listing the service");
    }
}
