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

package org.apache.camel.model;

import jakarta.xml.bind.annotation.XmlEnum;
import jakarta.xml.bind.annotation.XmlType;

@XmlType
@XmlEnum
public enum ThrottlingMode {
    /**
     * Uses a throttling mode that considers the total number of requests over defined period of time
     */
    TotalRequests,

    /**
     * Uses a throttling mode that uses a leaky-bucket algorithm to limit the outflow based on a maximum number of
     * concurrent requests
     */
    ConcurrentRequests;

    public static ThrottlingMode toMode(String mode) {
        if (mode.equals(ThrottlingMode.TotalRequests.name())) {
            return ThrottlingMode.TotalRequests;
        }

        return ThrottlingMode.ConcurrentRequests;
    }
}
