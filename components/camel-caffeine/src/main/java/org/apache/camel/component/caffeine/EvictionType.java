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
package org.apache.camel.component.caffeine;

public enum EvictionType {

    // type
    SIZE_BASED("size_based"),
    TIME_BASED("time_based");

    private static final EvictionType[] VALUES = values();
    private final String type;

    EvictionType(String type) {
        this.type = type;
    }

    public static EvictionType getEvictionType(String name) {
        if (name == null) {
            return null;
        }
        for (EvictionType evictionType : VALUES) {
            if (evictionType.toString().equalsIgnoreCase(name) || evictionType.name().equalsIgnoreCase(name)) {
                return evictionType;
            }
        }
        throw new IllegalArgumentException(String.format("Eviction type '%s' is not supported by this component.", name));
    }

    @Override
    public String toString() {
        return type;
    }

}
