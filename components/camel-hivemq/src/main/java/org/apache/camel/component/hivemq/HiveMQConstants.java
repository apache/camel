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
package org.apache.camel.component.hivemq;

import org.apache.camel.spi.Metadata;

public final class HiveMQConstants {

    @Metadata(description = "The topic to publish/subscribe to.", javaType = "String")
    public static final String MQTT_TOPIC = "CamelHiveMQTopic";

    @Metadata(description = "The QoS level of the message.", javaType = "com.hivemq.client.mqtt.datatypes.MqttQos")
    public static final String MQTT_QOS = "CamelHiveMQQos";

    @Metadata(description = "Whether the message should be retained.", javaType = "Boolean")
    public static final String MQTT_RETAINED = "CamelHiveMQRetained";

    @Metadata(description = "Header to dynamically override the target topic for publishing.", javaType = "String")
    public static final String OVERRIDE_TOPIC = "CamelHiveMQOverrideTopic";

    public static final String DEFAULT_HOST = "localhost";
    public static final int DEFAULT_PORT = 1883;
    public static final int DEFAULT_CONNECT_TIMEOUT_SECONDS = 30;

    private HiveMQConstants() {
    }
}
