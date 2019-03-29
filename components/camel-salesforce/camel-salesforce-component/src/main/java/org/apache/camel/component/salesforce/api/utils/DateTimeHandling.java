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
package org.apache.camel.component.salesforce.api.utils;

import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;

import static java.time.temporal.ChronoField.HOUR_OF_DAY;
import static java.time.temporal.ChronoField.MINUTE_OF_HOUR;
import static java.time.temporal.ChronoField.NANO_OF_SECOND;
import static java.time.temporal.ChronoField.SECOND_OF_MINUTE;

final class DateTimeHandling {

    static final DateTimeFormatter ISO_OFFSET_DATE_TIME = new DateTimeFormatterBuilder()//
        .parseCaseInsensitive()//
        .append(DateTimeFormatter.ISO_LOCAL_DATE)//
        .appendLiteral('T')//
        .appendValue(HOUR_OF_DAY, 2)//
        .appendLiteral(':')//
        .appendValue(MINUTE_OF_HOUR, 2)//
        .appendLiteral(':')//
        .appendValue(SECOND_OF_MINUTE, 2)//
        .optionalStart()//
        .appendFraction(NANO_OF_SECOND, 3, 3, true)//
        .optionalEnd()//
        .appendOffset("+HHMM", "Z")//
        .toFormatter();

    static final DateTimeFormatter ISO_OFFSET_TIME = new DateTimeFormatterBuilder()//
        .parseCaseInsensitive()//
        .appendValue(HOUR_OF_DAY, 2)//
        .appendLiteral(':')//
        .appendValue(MINUTE_OF_HOUR, 2)//
        .appendLiteral(':')//
        .appendValue(SECOND_OF_MINUTE, 2)//
        .optionalStart()//
        .appendFraction(NANO_OF_SECOND, 3, 3, true)//
        .optionalEnd()//
        .appendOffset("+HHMM", "Z")//
        .toFormatter();

    private DateTimeHandling() {
    }
}
