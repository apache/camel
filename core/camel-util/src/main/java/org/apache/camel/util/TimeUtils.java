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
     * Prints the since ago in a human-readable format as 9s, 27m44s, 3h12m, 3d8h, as seen on Kubernetes etc.
     *
     * @param  time time of the event (millis since epoch)
     * @return      ago in human-readable since the given time.
     */
    public static String printSince(long time) {
        long age = System.currentTimeMillis() - time;
        return printDuration(age, false);
    }

    /**
     * Prints the ago in a human-readable format as 9s, 27m44s, 3h12m, 3d8h, as seen on Kubernetes etc.
     *
     * @param  age age in millis
     * @return     ago in human-readable.
     */
    public static String printAge(long age) {
        return printDuration(age, false);
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
        return Duration.ofMillis(toMilliSeconds(source));
    }

    /**
     * Converts to milliseconds.
     *
     * @param  source duration which can be in text format such as 15s
     * @return        time in millis, will return 0 if the input is null or empty
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

        long days = 0;
        long hours = 0;
        long minutes = 0;
        long seconds = 0;
        long millis = 0;

        int pos = source.indexOf('d');
        if (pos != -1) {
            String s = source.substring(0, pos);
            days = Long.parseLong(s);
            source = source.substring(pos + 1);
        }

        pos = source.indexOf('h');
        if (pos != -1) {
            String s = source.substring(0, pos);
            hours = Long.parseLong(s);
            source = source.substring(pos + 1);
        }

        pos = source.indexOf('m');
        if (pos != -1) {
            boolean valid;
            if (source.length() - 1 <= pos) {
                valid = true;
            } else {
                // beware of minutes and not milliseconds
                valid = source.charAt(pos + 1) != 's';
            }
            if (valid) {
                String s = source.substring(0, pos);
                minutes = Long.parseLong(s);
                source = source.substring(pos + 1);
            }
        }

        pos = source.indexOf('s');
        // beware of seconds and not milliseconds
        if (pos != -1 && source.charAt(pos - 1) != 'm') {
            String s = source.substring(0, pos);
            seconds = Long.parseLong(s);
            source = source.substring(pos + 1);
        }

        pos = source.indexOf("ms");
        if (pos != -1) {
            String s = source.substring(0, pos);
            millis = Long.parseLong(s);
        }

        long answer = millis;
        if (seconds > 0) {
            answer += 1000 * seconds;
        }
        if (minutes > 0) {
            answer += 60000 * minutes;
        }
        if (hours > 0) {
            answer += 3600000 * hours;
        }
        if (days > 0) {
            answer += 86400000 * days;
        }

        LOG.trace("source: [{}], milliseconds: {}", source, answer);

        return answer;
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
