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

import java.text.DecimalFormatSymbols;

/**
 * Unit utils.
 */
public final class UnitUtils {

    private UnitUtils() {
    }

    /**
     * If having a size in bytes and wanting to print this in human friendly\ format with xx kB, xx MB, xx GB instead of
     * a large byte number.
     *
     * @param bytes the value in bytes
     */
    public static String printUnitFromBytes(long bytes) {
        if (bytes < 0) {
            return "";
        }

        // http://stackoverflow.com/questions/3758606/how-to-convert-byte-size-into-human-readable-format-in-java
        int unit = 1000;
        if (bytes < unit) {
            return bytes + " B";
        }
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = String.valueOf("kMGTPE".charAt(exp - 1));
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }

    /**
     * If having a size in bytes and wanting to print this in human friendly\ format with xx kB, xx MB, xx GB instead of
     * a large byte number, using dot as decimal separator.
     *
     * @param bytes the value in bytes
     */
    public static String printUnitFromBytesDot(long bytes) {
        return printUnitFromBytes(bytes, '.');
    }

    /**
     * If having a size in bytes and wanting to print this in human friendly\ format with xx kB, xx MB, xx GB instead of
     * a large byte number.
     *
     * @param bytes   the value in bytes
     * @param decimal the decimal separator char to use
     */
    public static String printUnitFromBytes(long bytes, char decimal) {
        if (bytes < 0) {
            return "";
        }

        // http://stackoverflow.com/questions/3758606/how-to-convert-byte-size-into-human-readable-format-in-java
        int unit = 1000;
        if (bytes < unit) {
            return bytes + " B";
        }
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = String.valueOf("kMGTPE".charAt(exp - 1));
        String answer = String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);

        char sep = DecimalFormatSymbols.getInstance().getDecimalSeparator();
        if (decimal != sep) {
            answer = answer.replace(sep, decimal);
        }
        return answer;
    }

}
