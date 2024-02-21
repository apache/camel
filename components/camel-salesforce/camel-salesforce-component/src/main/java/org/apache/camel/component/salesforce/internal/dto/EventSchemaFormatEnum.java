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
package org.apache.camel.component.salesforce.internal.dto;

public enum EventSchemaFormatEnum {

    // Apache Avro format but doesn't strictly adhere to the record complex type.
    EXPANDED("EXPANDED"),
    // Adheres to the Apache Avro specification for the record complex type
    COMPACT("COMPACT");

    final String value;

    EventSchemaFormatEnum(String value) {
        this.value = value;
    }

    public String value() {
        return this.value;
    }

    public static EventSchemaFormatEnum fromValue(String value) {
        for (EventSchemaFormatEnum e : EventSchemaFormatEnum.values()) {
            if (e.value.equals(value)) {
                return e;
            }
        }
        throw new IllegalArgumentException(value);
    }
}
