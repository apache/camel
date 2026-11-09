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
package org.apache.camel.component.vertx.mqtt;

/**
 * The versions of the MQTT protocol the component speaks, named as in the HiveMQ component.
 */
public enum VertxMqttVersion {

    MQTT_3_1_1(4),
    MQTT_5_0(5);

    private final int protocolLevel;

    VertxMqttVersion(int protocolLevel) {
        this.protocolLevel = protocolLevel;
    }

    /**
     * @return the protocol level of the CONNECT packet, as the Vert.x client takes it
     */
    public int protocolLevel() {
        return protocolLevel;
    }
}
