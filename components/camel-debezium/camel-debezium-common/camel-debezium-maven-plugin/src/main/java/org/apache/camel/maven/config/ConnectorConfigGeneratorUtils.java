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
package org.apache.camel.maven.config;

import java.time.Duration;

public final class ConnectorConfigGeneratorUtils {

    private static final int ONE_UNIT = 1;

    private ConnectorConfigGeneratorUtils() {
    }

    /**
     * This will print time in human readable format from milliseconds. Examples: 500 -> will produce 500ms 1300 -> will
     * produce 1s300ms 310300 -> will produce 5m10s300ms 6600000 -> will produce 1h50m
     *
     * @param  timeMilli time in milliseconds
     * @return           time in string
     */
    public static String toTimeAsString(final long timeMilli) {
        if (timeMilli < 1) {
            return "0ms";
        }
        final StringBuilder stringBuilder = new StringBuilder();
        scanTimeUnits(timeMilli, stringBuilder);

        return stringBuilder.toString();
    }

    private static long scanTimeUnits(final long timeInMillis, final StringBuilder timeBuilder) {
        if (timeInMillis >= Duration.ofDays(ONE_UNIT).toMillis()) {
            final long proceededTime
                    = processSingleTimeUnit(timeInMillis, Duration.ofDays(ONE_UNIT).toMillis(), timeBuilder, "d");
            return scanTimeUnits(proceededTime, timeBuilder);
        } else if (timeInMillis >= Duration.ofHours(ONE_UNIT).toMillis()) {
            final long proceededTime
                    = processSingleTimeUnit(timeInMillis, Duration.ofHours(ONE_UNIT).toMillis(), timeBuilder, "h");
            return scanTimeUnits(proceededTime, timeBuilder);
        } else if (timeInMillis >= Duration.ofMinutes(ONE_UNIT).toMillis()) {
            final long proceededTime
                    = processSingleTimeUnit(timeInMillis, Duration.ofMinutes(ONE_UNIT).toMillis(), timeBuilder, "m");
            return scanTimeUnits(proceededTime, timeBuilder);
        } else if (timeInMillis >= Duration.ofSeconds(ONE_UNIT).toMillis()) {
            final long proceededTime
                    = processSingleTimeUnit(timeInMillis, Duration.ofSeconds(ONE_UNIT).toMillis(), timeBuilder, "s");
            return scanTimeUnits(proceededTime, timeBuilder);
        } else if (timeInMillis >= ONE_UNIT) {
            final long proceededTime = processSingleTimeUnit(timeInMillis, ONE_UNIT, timeBuilder, "ms");
            return scanTimeUnits(proceededTime, timeBuilder);
        }
        return 0;
    }

    private static long processSingleTimeUnit(
            final long timeInMillis, final long timeUnitMillis, final StringBuilder timeBuilder, final String timeSymbol) {
        timeBuilder.append(calculateTimeUnit(timeInMillis, timeUnitMillis)).append(timeSymbol);
        return timeInMillis % timeUnitMillis;
    }

    private static long calculateTimeUnit(final long timeInMillis, final long baseTimeUnit) {
        return timeInMillis / baseTimeUnit;
    }

}
