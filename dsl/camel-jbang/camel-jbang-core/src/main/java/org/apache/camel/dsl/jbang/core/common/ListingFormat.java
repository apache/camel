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
package org.apache.camel.dsl.jbang.core.common;

import java.util.Comparator;
import java.util.function.Function;

/**
 * The values the listings of running integrations show the same way, such as the short form of an endpoint URI.
 */
public final class ListingFormat {

    private ListingFormat() {
    }

    /**
     * The endpoint URI, without its query parameters when short.
     */
    public static String uri(String uri, boolean shortUri) {
        if (shortUri && uri != null) {
            int pos = uri.indexOf('?');
            if (pos > 0) {
                return uri.substring(0, pos);
            }
        }
        return uri;
    }

    /**
     * The time since the last started, completed and failed exchange, such as <tt>1s/2s/-</tt>.
     */
    public static String sinceLast(String started, String completed, String failed) {
        return orDash(started) + "/" + orDash(completed) + "/" + orDash(failed);
    }

    /**
     * The change of the processing time, with a + in front when it got slower.
     */
    public static String delta(String delta) {
        if (delta != null && !delta.startsWith("-") && !"0".equals(delta)) {
            return "+" + delta;
        }
        return delta;
    }

    /**
     * The load averages of the last 1, 5 and 15 minutes, such as <tt>1.00/0.50/-</tt>, or <tt>0/0/0</tt> without load.
     */
    public static String load(String load01, String load05, String load15) {
        String s1 = noLoad(load01);
        String s2 = noLoad(load05);
        String s3 = noLoad(load15);
        if (s1.equals("-") && s2.equals("-") && s3.equals("-")) {
            return "0/0/0";
        }
        return s1 + "/" + s2 + "/" + s3;
    }

    /**
     * The order of the top commands: the slowest first by the mean processing time, or the fastest first when the sort
     * key has a - in front.
     *
     * @param sort the --sort option
     * @param mean the mean processing time of the row, in millis as text
     */
    public static <R> Comparator<R> slowestFirst(String sort, Function<R, String> mean) {
        Comparator<R> slowest = Comparator.comparingLong((R r) -> {
            String m = mean.apply(r);
            return m != null ? Long.parseLong(m) : 0;
        }).reversed();
        return sort != null && sort.startsWith("-") ? slowest.reversed() : slowest;
    }

    private static String orDash(String value) {
        return value != null ? value : "-";
    }

    private static String noLoad(String load) {
        return load == null || "0.00".equals(load) ? "-" : load;
    }
}
