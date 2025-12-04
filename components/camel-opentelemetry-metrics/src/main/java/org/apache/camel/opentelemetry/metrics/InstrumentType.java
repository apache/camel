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

package org.apache.camel.opentelemetry.metrics;

import org.apache.camel.RuntimeCamelException;

public enum InstrumentType {
    COUNTER("counter"),
    TIMER("timer"),
    DISTRIBUTION_SUMMARY("summary");

    InstrumentType(String name) {
        this.name = name;
    }

    private final String name;

    public String getName() {
        return name;
    }

    public static InstrumentType getByName(String name) {
        for (InstrumentType type : values()) {
            if (type.name.equalsIgnoreCase(name)) {
                return type;
            }
        }
        throw new RuntimeCamelException("Unsupported instrument type " + name);
    }
}
