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
package org.apache.camel.component.mtconnect;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One observation of a streams document with the device and component stream it came in.
 *
 * @param category    SAMPLE, EVENT or CONDITION, from the Samples, Events or Condition element that holds it
 * @param elementName the local element name: the observation type, or the level for a condition
 * @param attributes  the optional attributes of the observation element
 * @param value       the typed value: text, a decimal, a list of decimals, a map, or null when unavailable or empty
 * @param unavailable whether the agent reported the value UNAVAILABLE or the condition level Unavailable
 */
record Observation(
        String deviceUuid, String deviceName, String componentId, String componentType, String componentName,
        String dataItemId, String category, String elementName, long sequence, Instant timestamp, String name,
        String subType, String type, Attributes attributes, Object value, boolean unavailable) {

    /** The optional attributes of an observation element; null when the element does not carry them. */
    record Attributes(
            String nativeCode, String nativeSeverity, String qualifier, String compositionId, String resetTriggered,
            String statistic, BigDecimal duration, BigDecimal sampleRate, String assetType) {
    }
}
