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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The CVSS 3.0 / 3.1 base score of a vector such as {@code CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H} (9.8), with
 * the formula of the CVSS 3.1 specification. CVSS 4.0 vectors are scored with lookup tables, which this does not have:
 * they get no score.
 */
final class CvssScore {

    private CvssScore() {
    }

    /** The base score from 0.0 to 10.0, or null for a vector that is not a complete CVSS 3.x vector. */
    static Double baseScore(String vector) {
        if (vector == null || !(vector.startsWith("CVSS:3.0/") || vector.startsWith("CVSS:3.1/"))) {
            return null;
        }
        Map<String, String> m = new HashMap<>();
        for (String part : vector.substring(9).split("/")) {
            int colon = part.indexOf(':');
            if (colon > 0) {
                m.put(part.substring(0, colon), part.substring(colon + 1));
            }
        }
        boolean changed = "C".equals(m.get("S"));
        Double av = weight(m.get("AV"), "N", 0.85, "A", 0.62, "L", 0.55, "P", 0.2);
        Double ac = weight(m.get("AC"), "L", 0.77, "H", 0.44);
        Double pr = changed
                ? weight(m.get("PR"), "N", 0.85, "L", 0.68, "H", 0.5)
                : weight(m.get("PR"), "N", 0.85, "L", 0.62, "H", 0.27);
        Double ui = weight(m.get("UI"), "N", 0.85, "R", 0.62);
        Double c = cia(m.get("C"));
        Double i = cia(m.get("I"));
        Double a = cia(m.get("A"));
        if (av == null || ac == null || pr == null || ui == null || c == null || i == null || a == null
                || !m.containsKey("S")) {
            return null;
        }
        double iss = 1 - (1 - c) * (1 - i) * (1 - a);
        double impact = changed ? 7.52 * (iss - 0.029) - 3.25 * Math.pow(iss - 0.02, 15) : 6.42 * iss;
        if (impact <= 0) {
            return 0.0;
        }
        double exploitability = 8.22 * av * ac * pr * ui;
        double base = changed ? 1.08 * (impact + exploitability) : impact + exploitability;
        return roundUp(Math.min(base, 10));
    }

    /** The severity of a score: NONE, LOW, MEDIUM, HIGH or CRITICAL. */
    static String severity(double score) {
        if (score >= 9.0) {
            return "CRITICAL";
        }
        if (score >= 7.0) {
            return "HIGH";
        }
        if (score >= 4.0) {
            return "MEDIUM";
        }
        return score > 0 ? "LOW" : "NONE";
    }

    /** The score with one decimal, as it is shown; blank when there is none. */
    static String format(Double score) {
        return score != null ? String.format(Locale.ROOT, "%.1f", score) : "";
    }

    private static Double cia(String value) {
        return weight(value, "H", 0.56, "L", 0.22, "N", 0.0);
    }

    private static Double weight(String value, Object... pairs) {
        for (int k = 0; k < pairs.length; k += 2) {
            if (pairs[k].equals(value)) {
                return (Double) pairs[k + 1];
            }
        }
        return null;
    }

    /** The Roundup function of CVSS 3.1: the smallest number with one decimal that is not below the input. */
    private static double roundUp(double value) {
        long scaled = Math.round(value * 100000);
        if (scaled % 10000 == 0) {
            return scaled / 100000.0;
        }
        return (Math.floor(scaled / 10000.0) + 1) / 10.0;
    }
}
