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

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Time utils.
 */
public final class TimeUtils {

    private static final Logger LOG = LoggerFactory.getLogger(TimeUtils.class);
    private static final Pattern NUMBERS_ONLY_STRING_PATTERN = Pattern.compile("^[-]?(\\d)+$", Pattern.CASE_INSENSITIVE);
    private static final Pattern HOUR_REGEX_PATTERN = Pattern.compile("((\\d)*(\\d))h(our(s)?)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern MINUTES_REGEX_PATTERN = Pattern.compile("((\\d)*(\\d))m(in(ute(s)?)?)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern SECONDS_REGEX_PATTERN = Pattern.compile("((\\d)*(\\d))s(ec(ond)?(s)?)?", Pattern.CASE_INSENSITIVE);

    private TimeUtils() {
    }

    /**
     * Prints the duration in a human readable format as X days Y hours Z minutes etc.
     *
     * @param uptime the uptime in millis
     * @return the time used for displaying on screen or in logs
     */
    public static String printDuration(double uptime) {
        // Code taken from Karaf
        // https://svn.apache.org/repos/asf/karaf/trunk/shell/commands/src/main/java/org/apache/karaf/shell/commands/impl/InfoAction.java

        NumberFormat fmtI = new DecimalFormat("###,###", new DecimalFormatSymbols(Locale.ENGLISH));
        NumberFormat fmtD = new DecimalFormat("###,##0.000", new DecimalFormatSymbols(Locale.ENGLISH));

        uptime /= 1000;
        if (uptime < 60) {
            return fmtD.format(uptime) + " seconds";
        }
        uptime /= 60;
        if (uptime < 60) {
            long minutes = (long) uptime;
            String s = fmtI.format(minutes) + (minutes > 1 ? " minutes" : " minute");
            return s;
        }
        uptime /= 60;
        if (uptime < 24) {
            long hours = (long) uptime;
            long minutes = (long) ((uptime - hours) * 60);
            String s = fmtI.format(hours) + (hours > 1 ? " hours" : " hour");
            if (minutes != 0) {
                s += " " + fmtI.format(minutes) + (minutes > 1 ? " minutes" : " minute");
            }
            return s;
        }
        uptime /= 24;
        long days = (long) uptime;
        long hours = (long) ((uptime - days) * 24);
        String s = fmtI.format(days) + (days > 1 ? " days" : " day");
        if (hours != 0) {
            s += " " + fmtI.format(hours) + (hours > 1 ? " hours" : " hour");
        }
        return s;
    }

    public static long toMilliSeconds(String source) throws IllegalArgumentException {
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

        long milliseconds = 0;
        boolean foundFlag = false;

        checkCorrectnessOfPattern(source);
        Matcher matcher;

        matcher = createMatcher(NUMBERS_ONLY_STRING_PATTERN, source);
        if (matcher.find()) {
            // Note: This will also be used for regular numeric strings.
            //       This String -> long converter will be used for all strings.
            milliseconds = Long.parseLong(source);
        } else {
            matcher = createMatcher(HOUR_REGEX_PATTERN, source);
            if (matcher.find()) {
                milliseconds = milliseconds + (3600000 * Long.parseLong(matcher.group(1)));
                foundFlag = true;
            }

            matcher = createMatcher(MINUTES_REGEX_PATTERN, source);
            if (matcher.find()) {
                long minutes = Long.parseLong(matcher.group(1));
                if ((minutes > 59) && foundFlag) {
                    throw new IllegalArgumentException("Minutes should contain a valid value between 0 and 59: " + source);
                }
                foundFlag = true;
                milliseconds = milliseconds + (60000 * minutes);
            }

            matcher = createMatcher(SECONDS_REGEX_PATTERN, source);
            if (matcher.find()) {
                long seconds = Long.parseLong(matcher.group(1));
                if ((seconds > 59) && foundFlag) {
                    throw new IllegalArgumentException("Seconds should contain a valid value between 0 and 59: " + source);
                }
                foundFlag = true;
                milliseconds = milliseconds + (1000 * seconds);
            }

            // No pattern matched... initiating fallback check and conversion (if required).
            // The source at this point may contain illegal values or special characters
            if (!foundFlag) {
                milliseconds = Long.parseLong(source);
            }
        }

        LOG.trace("source: [{}], milliseconds: {}", source, milliseconds);

        return milliseconds;
    }

    private static void checkCorrectnessOfPattern(String source) {
        //replace only numbers once
        Matcher matcher = createMatcher(NUMBERS_ONLY_STRING_PATTERN, source);
        String replaceSource = matcher.replaceFirst("");

        //replace hour string once
        matcher = createMatcher(HOUR_REGEX_PATTERN, replaceSource);
        if (matcher.find() && matcher.find()) {
            throw new IllegalArgumentException("Hours should not be specified more then once: " + source);
        }
        replaceSource = matcher.replaceFirst("");

        //replace minutes once
        matcher = createMatcher(MINUTES_REGEX_PATTERN, replaceSource);
        if (matcher.find() && matcher.find()) {
            throw new IllegalArgumentException("Minutes should not be specified more then once: " + source);
        }
        replaceSource = matcher.replaceFirst("");

        //replace seconds once
        matcher = createMatcher(SECONDS_REGEX_PATTERN, replaceSource);
        if (matcher.find() && matcher.find()) {
            throw new IllegalArgumentException("Seconds should not be specified more then once: " + source);
        }
        replaceSource = matcher.replaceFirst("");

        if (replaceSource.length() > 0) {
            throw new IllegalArgumentException("Illegal characters: " + source);
        }
    }

    private static Matcher createMatcher(Pattern pattern, String source) {
        return pattern.matcher(source);
    }

}
