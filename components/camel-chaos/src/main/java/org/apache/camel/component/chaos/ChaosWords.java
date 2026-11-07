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
package org.apache.camel.component.chaos;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.StringJoiner;

/**
 * Times, counts and shares in words, for {@link ChaosRule#describe()}: {@code 5 minutes}, {@code 1 minute 30 seconds},
 * {@code 1.5 seconds}, {@code 100 sends}, {@code 30%}.
 */
final class ChaosWords {

    private static final long SECOND = 1000;
    private static final long MINUTE = 60 * SECOND;
    private static final long HOUR = 60 * MINUTE;
    private static final long DAY = 24 * HOUR;

    private ChaosWords() {
    }

    /**
     * A time in words: the days, hours, minutes and seconds that are not 0 ({@code 1 hour 30 minutes}), the seconds
     * with their fraction under a minute ({@code 1.5 seconds}), and milliseconds under a second.
     */
    static String time(long millis) {
        if (millis < SECOND) {
            return count(millis, "millisecond", "milliseconds");
        }
        if (millis < MINUTE && millis % SECOND != 0) {
            BigDecimal seconds = BigDecimal.valueOf(millis).movePointLeft(3).stripTrailingZeros();
            return seconds.toPlainString() + " seconds";
        }
        StringJoiner sj = new StringJoiner(" ");
        long rest = millis;
        rest = part(sj, rest, DAY, "day", "days");
        rest = part(sj, rest, HOUR, "hour", "hours");
        rest = part(sj, rest, MINUTE, "minute", "minutes");
        rest = part(sj, rest, SECOND, "second", "seconds");
        if (rest > 0) {
            sj.add(count(rest, "millisecond", "milliseconds"));
        }
        return sj.toString();
    }

    private static long part(StringJoiner sj, long millis, long unit, String one, String many) {
        long n = millis / unit;
        if (n > 0) {
            sj.add(count(n, one, many));
        }
        return millis % unit;
    }

    /** A count with its noun: {@code 1 send}, {@code 100 sends}. */
    static String count(long n, String one, String many) {
        return n + " " + (n == 1 ? one : many);
    }

    /** A share as a percent, with at most two decimals: {@code 30%}, {@code 33.33%}, {@code 0.5%}. */
    static String percent(double share) {
        BigDecimal p = BigDecimal.valueOf(share * 100).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
        return p.toPlainString() + "%";
    }
}
