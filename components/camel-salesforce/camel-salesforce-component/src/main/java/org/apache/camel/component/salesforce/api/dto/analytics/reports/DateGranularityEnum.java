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
package org.apache.camel.component.salesforce.api.dto.analytics.reports;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Report results groupings date granularity.
 */
public enum DateGranularityEnum {

    // None
    NONE("None"),
    // Day
    DAY("Day"),
    // Calendar Week
    CALENDAR_WEEK("Calendar Week"),
    // Calendar Month
    CALENDAR_MONTH("Calendar Month"),
    // Calendar Quarter
    CALENDAR_QUARTER("Calendar Quarter"),
    // Calendar Year
    CALENDAR_YEAR("Calendar Year"),
    // Calendar Month in Year
    CALENDAR_MONTH_IN_YEAR("Calendar Month in Year"),
    // Calendar Day in Month
    CALENDAR_DAY_IN_MONTH("Calendar Day in Month"),
    // Fiscal Period
    FISCAL_PERIOD("Fiscal Period"),
    // Fiscal Week
    FISCAL_WEEK("Fiscal Week"),
    // Fiscal Quarter
    FISCAL_QUARTER("Fiscal Quarter"),
    // Fiscal Year
    FISCAL_YEAR("Fiscal Year");

    private final String value;

    DateGranularityEnum(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static DateGranularityEnum fromValue(String value) {
        for (DateGranularityEnum e : DateGranularityEnum.values()) {
            if (e.value.equals(value)) {
                return e;
            }
        }
        throw new IllegalArgumentException(value);
    }

}
