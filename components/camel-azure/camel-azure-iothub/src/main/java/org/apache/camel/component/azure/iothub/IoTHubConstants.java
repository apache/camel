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
package org.apache.camel.component.azure.iothub;

import org.apache.camel.spi.Metadata;

/**
 * Constants (message headers) used by the Azure IoT Hub component.
 */
public final class IoTHubConstants {

    private static final String HEADER_PREFIX = "CamelAzureIoTHub";

    @Metadata(label = "producer",
              description = "The ID of the device that sends the message. It replaces the deviceId of the endpoint, unless it"
                            + " is empty.",
              javaType = "String")
    public static final String DEVICE_ID = HEADER_PREFIX + "DeviceId";

    @Metadata(label = "producer",
              description = "The connection string of the device. It replaces the connectionString option, unless it is"
                            + " empty.",
              javaType = "String",
              secret = true)
    public static final String CONNECTION_STRING = HEADER_PREFIX + "ConnectionString";

    @Metadata(label = "producer",
              description = "What the producer sends for this message. It replaces the deliveryMode option.",
              javaType = "org.apache.camel.component.azure.iothub.IoTHubDeliveryMode")
    public static final String DELIVERY_MODE = HEADER_PREFIX + "DeliveryMode";

    @Metadata(label = "producer",
              description = "The reported properties to set on the device twin, as a java.util.Map with String keys. TWIN"
                            + " uses it instead of a Map body, and BOTH requires it. The producer sends only the properties"
                            + " whose value differs from the current reported properties.",
              javaType = "java.util.Map")
    public static final String TWIN_PROPERTIES = HEADER_PREFIX + "TwinProperties";

    private IoTHubConstants() {
    }
}
