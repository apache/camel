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
package org.apache.camel.util;

import java.time.Duration;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Time utils.
 */
public final class TimeUtils {

    private static final Logger LOG = LoggerFactory.getLogger(TimeUtils.class);

    private TimeUtils() {
    }

    public static boolean isPositive(Duration dur) {
        return dur.getSeconds() > 0 || dur.getNano() != 0;
    }

    /**
     * Prints since age in a human-readable format as 9s, 27m44s, 3h12m, 3d8h, as seen on Kubernetes etc.
     *
     * @param  time time of the event (millis since epoch)
     * @return      age in human-readable since the given time.
     */
    public static String printSince(long time) {
        long age = System.currentTimeMillis() - time;
        return printDuration(age, false);
    }

    /**
     * Prints since age in a human-readable format as 9s, 27m44s, 3h12m, 3d8h, as seen on Kubernetes etc.
     *
     * @param  time    time of the event (millis since epoch)
     * @param  precise whether to be precise and include more details
     * @return         age in human-readable since the given time.
     */
    public static String printSince(long time, boolean precise) {
        long age = System.currentTimeMillis() - time;
        return printDuration(age, precise);
    }

    /**
     * Prints the age in a human-readable format as 9s, 27m44s, 3h12m, 3d8h, as seen on Kubernetes etc.
     *
     * @param  age age in millis
     * @return     age in human-readable.
     */
    public static String printAge(long age) {
        return printDuration(age, false);
    }

    /**
     * Prints the age in a human-readable format as 9s, 27m44s, 3h12m, 3d8h, as seen on Kubernetes etc.
     *
     * @param  age     age in millis
     * @param  precise whether to be precise and include more details
     * @return         age in human-readable.
     */
    public static String printAge(long age, boolean precise) {
        return printDuration(age, precise);
    }

    /**
     * Prints the duration in a human-readable format as 9s, 27m44s, 3h12m, 3d8h, etc.
     *
     * @param  uptime the uptime in millis
     * @return        the time used for displaying on screen or in logs
     */
    public static String printDuration(Duration uptime) {
        return printDuration(uptime, false);
    }

    /**
     * Prints the duration in a human-readable format as 9s, 27m44s, 3h12m, 3d8h, etc.
     *
     * @param  uptime  the uptime in millis
     * @param  precise whether to be precise and include more details
     * @return         the time used for displaying on screen or in logs
     */
    public static String printDuration(Duration uptime, boolean precise) {
        return printDuration(uptime.toMillis(), precise);
    }

    /**
     * Prints the duration in a human-readable format as 9s, 27m44s, 3h12m, 3d8h, etc.
     *
     * @param  uptime the uptime in millis
     * @return        the time used for displaying on screen or in logs
     */
    public static String printDuration(long uptime) {
        return printDuration(uptime, false);
    }

    /**
     * Prints the duration in a human-readable format as 9s, 27m44s, 3h12m, 3d8h, etc.
     *
     * @param  uptime  the uptime in millis
     * @param  precise whether to be precise and include more details
     * @return         the time used for displaying on screen or in logs
     */
    public static String printDuration(long uptime, boolean precise) {
        if (uptime <= 0) {
            return "0ms";
        }

        StringBuilder sb = new StringBuilder();

        long seconds = uptime / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        long millis = 0;
        if (uptime > 1000) {
            millis = uptime % 1000;
        } else if (uptime < 1000) {
            millis = uptime;
        }

        if (days > 0) {
            sb.append(days).append("d").append(hours % 24).append("h");
            if (precise) {
                sb.append(minutes % 60).append("m").append(seconds % 60).append("s");
            }
        } else if (hours > 0) {
            sb.append(hours % 24).append("h").append(minutes % 60).append("m");
            if (precise) {
                sb.append(seconds % 60).append("s");
            }
        } else if (minutes > 0) {
            sb.append(minutes % 60).append("m").append(seconds % 60).append("s");
            if (precise) {
                sb.append(millis).append("ms");
            }
        } else if (seconds > 0) {
            sb.append(seconds % 60).append("s");
            if (precise) {
                sb.append(millis).append("ms");
            }
        } else if (millis > 0) {
            if (!precise) {
                // less than a second so just report it as zero
                sb.append("0s");
            } else {
                sb.append(millis).append("ms");
            }
        }

        return sb.toString();
    }

    /**
     * Converts to duration.
     *
     * @param source duration which can be in text format such as 15s
     */
    public static Duration toDuration(String source) {
        if (source.startsWith("P") || source.startsWith("-P") || source.startsWith("p") || source.startsWith("-p")) {
            return Duration.parse(source);
        } else {
            return Duration.ofMillis(toMilliSeconds(source));
        }
    }

    /**
     * Converts to milliseconds.
     *
     * @param  source                   duration which can be in text format such as 15s
     * @return                          time in millis, will return 0 if the input is null or empty
     * @throws IllegalArgumentException if the input is not a number or a valid time pattern
     */
    public static long toMilliSeconds(String source) {
        if (source == null || source.isEmpty()) {
            return 0;
        }

        // quick conversion if its only digits
        boolean digit = true;
        for (int i = 0; i < source.length(); i++) {
            char ch = source.charAt(i);
            // special for fist as it can be negative number
            if (i == 0 && ch == '-') {
                continue;
            }
            // quick check if its 0..9
            if (ch < '0' || ch > '9') {
                digit = false;
                break;
            }
        }
        if (digit) {
            return Long.parseLong(source);
        }

        long answer = parseTimePattern(source);
        LOG.trace("input: [{}], milliseconds: {}", source, answer);
        return answer;
    }

    private static long parseTimePattern(String source) {
        String text = source.trim();
        boolean negative = text.startsWith("-");
        if (negative) {
            text = text.substring(1);
        }
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Invalid time pattern: " + source);
        }

        // the whole text must be groups of a number followed by an unit (such as 1h30m or 5s)
        long answer = 0;
        int len = text.length();
        int i = 0;
        while (i < len) {
            int start = i;
            while (i < len && Character.isDigit(text.charAt(i))) {
                i++;
            }
            if (start == i) {
                throw new IllegalArgumentException("Invalid time pattern: " + source);
            }
            long num = Long.parseLong(text.substring(start, i));
            start = i;
            while (i < len && Character.isLetter(text.charAt(i))) {
                i++;
            }
            String unit = text.substring(start, i).toLowerCase(Locale.ENGLISH);
            long factor = switch (unit) {
                case "d", "day", "days" -> 86400000L;
                case "h", "hour", "hours" -> 3600000L;
                case "m", "min", "mins", "minute", "minutes" -> 60000L;
                case "s", "sec", "secs", "second", "seconds" -> 1000L;
                case "ms", "milli", "millis" -> 1L;
                default -> throw new IllegalArgumentException("Invalid time pattern: " + source);
            };
            try {
                answer = Math.addExact(answer, Math.multiplyExact(num, factor));
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException("Invalid time pattern: " + source, e);
            }
            // allow whitespace between the groups such as 1h 30m
            while (i < len && Character.isWhitespace(text.charAt(i))) {
                i++;
            }
        }
        return negative ? -answer : answer;
    }

    /**
     * Elapsed time using milliseconds since epoch.
     *
     * @param      start the timestamp in milliseconds since epoch
     * @return           the elapsed time in milliseconds
     * @deprecated       Use the Clock API when possible
     */
    @Deprecated(since = "4.4.0")
    public static long elapsedMillisSince(long start) {
        return System.currentTimeMillis() - start;
    }

}
