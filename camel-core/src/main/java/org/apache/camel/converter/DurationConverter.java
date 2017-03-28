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
package org.apache.camel.converter;

import java.time.Duration;
import org.apache.camel.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Converters for java.time.Duration.
 * Provides a converter from a string (ISO-8601) to a Duration,
 * a Duration to a string (ISO-8601) and
 * a Duration to millis (long)
 */
@Converter
public final class DurationConverter {
    private static final Logger LOG = LoggerFactory.getLogger(DurationConverter.class);

    /**
     * Utility classes should not have a public constructor.
     */
    private DurationConverter() {
    }
    
    @Converter
    public static long toMilliSeconds(Duration source) {
        long milliseconds = source.toMillis();
        LOG.trace("source: {} milliseconds: ", source, milliseconds);
        return milliseconds;
    }

    @Converter
    public static Duration fromString(String source) {
        Duration duration = Duration.parse(source);
        LOG.trace("source: {} milliseconds: ", source, duration);
        return duration;
    }

    @Converter
    public static String asString(Duration source) {
        String result = source.toString();
        LOG.trace("source: {} milliseconds: ", source, result);
        return result;
    }
}
