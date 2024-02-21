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
package org.apache.camel.component.paho;

import org.apache.camel.spi.Metadata;

/**
 * Constants to use when working with Paho component.
 */
public final class PahoConstants {

    /**
     * Header indicating a topic of a MQTT message.
     */
    @Metadata(label = "consumer", description = "The name of the topic.", javaType = "String")
    public static final String MQTT_TOPIC = "CamelMqttTopic";
    /**
     * Header indicating a QoS of a MQTT message.
     */
    @Metadata(label = "consumer", description = "The quality of service of the incoming message.", javaType = "Integer")
    public static final String MQTT_QOS = "CamelMqttQoS";

    public static final String DEFAULT_BROKER_URL = "tcp://localhost:1883";
    public static final int DEFAULT_QOS = 2;

    @Deprecated
    public static final String HEADER_ORIGINAL_MESSAGE = "PahoOriginalMessage";
    public static final String CAMEL_PAHO = "CamelPaho";
    @Metadata(label = "producer", description = "The client quality of service level (0-2).", javaType = "Integer")
    public static final String CAMEL_PAHO_MSG_QOS = CAMEL_PAHO + "Qos";
    @Metadata(label = "producer", description = "Retain option.", javaType = "Boolean")
    public static final String CAMEL_PAHO_MSG_RETAINED = CAMEL_PAHO + "Retained";
    @Metadata(label = "producer",
              description = "The name of topic to override and send to instead of topic specified on endpoint.",
              javaType = "String")
    public static final String CAMEL_PAHO_OVERRIDE_TOPIC = CAMEL_PAHO + "OverrideTopic";

    private PahoConstants() {
    }
}
