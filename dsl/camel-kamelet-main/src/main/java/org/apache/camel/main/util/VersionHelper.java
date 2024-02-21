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
package org.apache.camel.main.util;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.util.StringHelper;

public final class VersionHelper {

    private static final String KAMELETS_DEFAULT_VERSION = "4.0.0-RC1";
    private static final Pattern KAMELETS_LIBRARY = Pattern.compile("camel-kamelets-(\\d[A-Z\\d.-]*).jar", Pattern.DOTALL);
    private static final String CP = System.getProperty("java.class.path");

    private VersionHelper() {
    }

    public static boolean isGE(String source, String target) {
        return compare(source, target) >= 0;
    }

    public static int compare(String source, String target) {
        if (source == null || target == null) {
            return 0;
        }
        String s1 = StringHelper.before(source, ".");
        String s2 = StringHelper.after(source, ".");
        if (s1 == null) {
            s1 = StringHelper.before(source, ",");
            s2 = StringHelper.after(source, ",");
        }
        String t1 = StringHelper.before(target, ".");
        String t2 = StringHelper.after(target, ".");
        if (t1 == null) {
            t1 = StringHelper.before(target, ",");
            t2 = StringHelper.after(target, ",");
        }
        String s3 = StringHelper.after(s2, ".");
        if (s3 != null) {
            s2 = StringHelper.before(s2, ".");
        } else {
            s3 = "";
        }
        String t3 = StringHelper.after(t2, ".");
        if (t3 != null) {
            t2 = StringHelper.before(t2, ".");
        } else {
            t3 = "";
        }
        // convert to 2-digit numbers
        if (s1.length() < 2) {
            s1 = "0" + s1;
        }
        if (s2.length() < 2) {
            s2 = "0" + s2;
        }
        if (s2.length() < 2) {
            s2 = "0" + s2;
        }
        if (s3.length() < 2) {
            s3 = "0" + s3;
        }
        if (t1.length() < 2) {
            t1 = "0" + t1;
        }
        if (t2.length() < 2) {
            t2 = "0" + t2;
        }
        if (t3.length() < 2) {
            t3 = "0" + t3;
        }

        String s = s1 + s2 + s3;
        String t = t1 + t2 + t3;
        return s.compareTo(t);
    }

    public static String extractKameletsVersion() {
        Matcher matcher = KAMELETS_LIBRARY.matcher(CP);
        if (matcher.find() && matcher.groupCount() > 0) {
            return matcher.group(1);
        }

        RuntimeMXBean mb = ManagementFactory.getRuntimeMXBean();
        if (mb != null) {
            matcher = KAMELETS_LIBRARY.matcher(mb.getClassPath());
            if (matcher.find() && matcher.groupCount() > 0) {
                return matcher.group(1);
            }
        }

        return KAMELETS_DEFAULT_VERSION;
    }
}
