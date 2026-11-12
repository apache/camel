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

import java.util.Map;

/**
 * Immutable holder for the payloads a single dispatch may carry.
 *
 * <p>
 * A telemetry payload ({@code byte[]} or {@code String}) and/or a device-twin reported-properties map can be present.
 * For {@code BOTH} delivery mode both are populated: the telemetry payload is sent as a device-to-cloud message and the
 * twin map is applied as reported properties.
 * </p>
 *
 * @param telemetry      the telemetry payload ({@code byte[]} or {@code String}), or {@code null} if none
 * @param twinProperties the device-twin reported properties, or {@code null} if none
 */
public record DeliveryContext(Object telemetry, Map<String, Object> twinProperties) {

    /**
     * @return {@code true} if neither a telemetry payload nor twin properties are present
     */
    public boolean isEmpty() {
        return telemetry == null && (twinProperties == null || twinProperties.isEmpty());
    }
}
