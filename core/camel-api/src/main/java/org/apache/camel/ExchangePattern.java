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
package org.apache.camel;

import jakarta.xml.bind.annotation.XmlEnum;
import jakarta.xml.bind.annotation.XmlType;

/**
 * Represents the kind of message exchange pattern
 */
@XmlType
@XmlEnum
public enum ExchangePattern {
    InOnly,
    InOut;

    /**
     * Return true if there can be an IN message
     */
    public boolean isInCapable() {
        return true;
    }

    /**
     * Return true if there can be an OUT message
     */
    public boolean isOutCapable() {
        return this != ExchangePattern.InOnly;
    }

    public static ExchangePattern asEnum(String value) {
        try {
            return valueOf(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Unknown message exchange pattern: " + value, e);
        }
    }

}
