/**
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
package org.apache.camel.component.gora;

/**
 * Camel-Gora attributes
 */
public enum GoraAttribute {

    /**
     * Gora KEY attribute
     */
    GORA_KEY("goraKey"),

    /**
     * Gora operation header name
     */
    GORA_OPERATION("goraOperation"),

    /**
     * Gora Query Start Time attribute
     */
    GORA_QUERY_START_TIME("startTime"),

    /**
     * Gora Query End Time attribute
     */
    GORA_QUERY_END_TIME("endTime"),

    /**
     * Gora Query Start Key attribute
     */
    GORA_QUERY_START_KEY("startKey"),

    /**
     * Gora Query End Key attribute
     */
    GORA_QUERY_END_KEY("endKey"),

    /**
     * Gora Query Key Range From attribute
     */
    GORA_QUERY_KEY_RANGE_FROM("keyRangeFrom"),

    /**
     * Gora Query Key Range To attribute
     */
    GORA_QUERY_KEY_RANGE_TO("keyRangeTo"),

    /**
     * Gora Query Time Range From attribute
     */
    GORA_QUERY_TIME_RANGE_FROM("timeRangeFrom"),

    /**
     * Gora Query Key Range To attribute
     */
    GORA_QUERY_TIME_RANGE_TO("timeRangeTo"),

    /**
     * Gora Query Limit attribute
     */
    GORA_QUERY_LIMIT("limit"),

    /**
     * Gora Query Timestamp attribute
     */
    GORA_QUERY_TIMESTAMP("timestamp"),

    /**
     * Gora Query Fields attribute
     */
    GORA_QUERY_FIELDS("fields");

    /**
     * Enum value
     */
    public final String value;

    /**
     * Enum constructor
     *
     * @param str Operation Value
     */
    GoraAttribute(final String str) {
        value = str;
    }

}
