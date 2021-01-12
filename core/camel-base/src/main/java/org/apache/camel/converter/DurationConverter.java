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
package org.apache.camel.converter;

import java.time.Duration;

import org.apache.camel.Converter;
import org.apache.camel.util.TimeUtils;

/**
 * Converters for java.time.Duration.
 */
@Converter(generateBulkLoader = true)
public final class DurationConverter {

    /**
     * Utility classes should not have a public constructor.
     */
    private DurationConverter() {
    }

    @Converter(order = 1)
    public static Long toMilliSeconds(Duration source) {
        return source.toMillis();
    }

    @Converter(order = 2)
    public static Duration toDuration(Long source) {
        return Duration.ofMillis(source);
    }

    @Converter(order = 3)
    public static Duration toDuration(String source) {
        if (source.startsWith("P") || source.startsWith("-P") || source.startsWith("p") || source.startsWith("-p")) {
            return Duration.parse(source);
        } else {
            return Duration.ofMillis(TimeUtils.toMilliSeconds(source));
        }
    }

    @Converter(order = 4)
    public static String toString(Duration source) {
        return source.toString();
    }
}
