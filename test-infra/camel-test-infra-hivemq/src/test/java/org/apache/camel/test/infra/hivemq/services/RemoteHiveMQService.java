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
package org.apache.camel.test.infra.hivemq.services;

import java.util.Optional;

import org.apache.camel.test.infra.hivemq.common.HiveMQProperties;

public class RemoteHiveMQService implements HiveMQService {

    private volatile boolean running = false;

    @Override
    public void registerProperties() {
        // NO-OP
    }

    @Override
    public void initialize() {
        running = true;
    }

    @Override
    public void shutdown() {
        running = false;
    }

    @Override
    public int getMqttPort() {
        String mqttPort = System.getProperty(HiveMQProperties.HIVEMQ_SERVICE_MQTT_PORT, "1883");

        return Integer.parseInt(mqttPort);
    }

    @Override
    public String getMqttHost() {
        return System.getProperty(HiveMQProperties.HIVEMQ_SERVICE_MQTT_HOST, "localhost");
    }

    @Override
    public String getUserName() {
        return System.getProperty(HiveMQProperties.HIVEMQ_SERVICE_USER_NAME);
    }

    @Override
    public char[] getUserPassword() {
        return Optional.ofNullable(System.getProperty(HiveMQProperties.HIVEMQ_SERVICE_USER_PASSWORD)).map(String::toCharArray)
                .orElse(null);
    }

    @Override
    public boolean isRunning() {
        return running;
    }

}
