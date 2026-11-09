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
package org.apache.camel.component.azure.iothub.operations;

import com.microsoft.azure.sdk.iot.device.DeviceClient;
import org.apache.camel.component.azure.iothub.IoTHubDeliveryMode;

/**
 * Strategy for delivering a message body to Azure IoT Hub (telemetry or device twin).
 */
public interface DeliveryStrategy {

    /**
     * Delivers the body using the given, already-opened device client.
     *
     * @param  client    an active, authenticated {@link DeviceClient}; must not be closed by the strategy
     * @param  deviceId  the target device id (for logging)
     * @param  context   the dispatch payloads (telemetry byte[]/String and/or twin Map)
     * @throws Exception if delivery fails
     */
    void execute(DeviceClient client, String deviceId, DeliveryContext context) throws Exception;

    /**
     * @return {@code true} if this strategy participates for the given delivery mode
     */
    boolean supports(IoTHubDeliveryMode mode);
}
