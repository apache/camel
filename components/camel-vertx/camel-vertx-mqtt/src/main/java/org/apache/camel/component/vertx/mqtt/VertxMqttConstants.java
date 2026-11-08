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

import org.apache.camel.spi.Metadata;

/**
 * The message headers of the Vert.x MQTT component.
 */
public final class VertxMqttConstants {

    public static final String SCHEME = "vertx-mqtt";

    /**
     * The largest message the MQTT protocol allows, 268435455 bytes. The Vert.x client treats its default of -1 as the
     * 8092 bytes of the Netty decoder and closes the connection on a larger incoming message.
     */
    public static final int MAX_MESSAGE_SIZE = 268_435_455;

    @Metadata(label = "consumer", description = "The topic the message was published on.", javaType = "String")
    public static final String TOPIC = "CamelVertxMqttTopic";
    @Metadata(label = "consumer", description = "The quality of service the message was delivered with: 0, 1 or 2.",
              javaType = "Integer")
    public static final String QOS = "CamelVertxMqttQos";
    @Metadata(label = "consumer", description = "Whether the broker delivered a retained message.",
              javaType = "Boolean")
    public static final String RETAINED = "CamelVertxMqttRetained";
    @Metadata(label = "consumer", description = "Whether the broker delivers the message again.", javaType = "Boolean")
    public static final String DUPLICATE = "CamelVertxMqttDuplicate";
    @Metadata(label = "consumer", description = "The packet identifier of a message of quality of service 1 or 2.",
              javaType = "Integer")
    public static final String MESSAGE_ID = "CamelVertxMqttMessageId";
    @Metadata(label = "producer", description = "The topic to publish on instead of the topic of the endpoint.",
              javaType = "String")
    public static final String OVERRIDE_TOPIC = "CamelVertxMqttOverrideTopic";
    @Metadata(label = "producer",
              description = "The quality of service to publish with instead of the qos option of the endpoint: 0, 1 or 2.",
              javaType = "Integer")
    public static final String OVERRIDE_QOS = "CamelVertxMqttOverrideQos";
    @Metadata(label = "producer",
              description = "Whether to publish the message as retained, instead of the retained option of the endpoint.",
              javaType = "Boolean")
    public static final String OVERRIDE_RETAINED = "CamelVertxMqttOverrideRetained";
    @Metadata(description = "MQTT 5 only. The user properties of the message, in their order and with keys that occur"
                            + " more than once. The producer also takes a Map.",
              javaType = "java.util.List<java.util.Map.Entry<String, String>>")
    public static final String USER_PROPERTIES = "CamelVertxMqttUserProperties";
    @Metadata(description = "MQTT 5 only. The content type of the message.", javaType = "String")
    public static final String CONTENT_TYPE = "CamelVertxMqttContentType";
    @Metadata(description = "MQTT 5 only. The topic a response is expected on.", javaType = "String")
    public static final String RESPONSE_TOPIC = "CamelVertxMqttResponseTopic";
    @Metadata(description = "MQTT 5 only. The data that relates a response to its request.", javaType = "byte[]")
    public static final String CORRELATION_DATA = "CamelVertxMqttCorrelationData";
    @Metadata(description = "MQTT 5 only. The lifetime of the message in seconds.", javaType = "Long")
    public static final String MESSAGE_EXPIRY_INTERVAL = "CamelVertxMqttMessageExpiryInterval";

    private VertxMqttConstants() {
    }
}
