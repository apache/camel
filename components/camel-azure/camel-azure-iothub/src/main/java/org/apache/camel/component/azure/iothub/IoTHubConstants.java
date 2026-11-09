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
              description = "The target device id. Overrides the device id configured on the endpoint.",
              javaType = "String")
    public static final String DEVICE_ID = HEADER_PREFIX + "DeviceId";

    @Metadata(label = "producer",
              description = "The per-device IoT Hub connection string. Overrides the connection string "
                            + "configured on the endpoint.",
              javaType = "String")
    public static final String CONNECTION_STRING = HEADER_PREFIX + "ConnectionString";

    @Metadata(label = "producer",
              description = "Overrides the configured delivery mode for this message.",
              javaType = "org.apache.camel.component.azure.iothub.IoTHubDeliveryMode")
    public static final String DELIVERY_MODE = HEADER_PREFIX + "DeliveryMode";

    @Metadata(label = "producer",
              description = "Device twin reported properties to apply, as a Map<String, Object>. Used for TWIN "
                            + "delivery and, in BOTH mode, alongside the telemetry message body.",
              javaType = "java.util.Map")
    public static final String TWIN_PROPERTIES = HEADER_PREFIX + "TwinProperties";

    private IoTHubConstants() {
    }
}
