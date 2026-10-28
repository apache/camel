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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToLongFunction;

/**
 * The order of the rows of a listing by its <tt>--sort</tt> option: a key such as <tt>name</tt>, with a <tt>-</tt> in
 * front for the reverse order. A row without the value comes first, and a key the listing does not have is a usage
 * error.
 *
 * @param <R> the row of the listing
 */
public final class RowSort<R> {

    private final Map<String, Comparator<R>> keys = new LinkedHashMap<>();

    /**
     * The order of the rows of a listing of running integrations: by pid, name and age.
     *
     * @param pid    the pid of the row, as text
     * @param name   the name of the integration
     * @param uptime how long the integration runs, in millis
     */
    public static <R> RowSort<R> integration(Function<R, String> pid, Function<R, String> name, ToLongFunction<R> uptime) {
        return new RowSort<R>().number("pid", pid).text("name", name).by("age", Comparator.comparingLong(uptime));
    }

    /**
     * Sorts by a text, ignoring case.
     */
    public RowSort<R> text(String key, Function<R, String> value) {
        return by(key, Comparator.comparing(value, Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER)));
    }

    /**
     * Sorts by a whole number kept as text, such as a pid or a counter; a text that is not a number sorts first.
     */
    public RowSort<R> number(String key, Function<R, String> value) {
        return by(key, Comparator.comparing(row -> parse(value.apply(row)), Comparator.nullsFirst(Comparator.naturalOrder())));
    }

    /**
     * Sorts by a number.
     */
    public RowSort<R> numeric(String key, ToLongFunction<R> value) {
        return by(key, Comparator.comparingLong(value));
    }

    /**
     * Sorts by a comparator of the rows.
     */
    public RowSort<R> by(String key, Comparator<R> comparator) {
        keys.put(key, comparator);
        return this;
    }

    /**
     * Another key for the same order, such as <tt>support-level</tt> for <tt>level</tt>.
     */
    public RowSort<R> alias(String key, String existing) {
        return by(key, keys.get(existing));
    }

    /**
     * The order of the rows for the value of the <tt>--sort</tt> option.
     *
     * @param  sort         the key, with a <tt>-</tt> in front for the reverse order; <tt>null</tt> keeps the order of
     *                      the rows
     * @throws CliException (exit code 2) when the listing has no such key
     */
    public Comparator<R> comparator(String sort) {
        if (sort == null || sort.isBlank()) {
            return (o1, o2) -> 0;
        }
        boolean reverse = sort.startsWith("-");
        Comparator<R> order = keys.get(reverse ? sort.substring(1) : sort);
        if (order == null) {
            throw CliException.usage("--sort must be one of " + String.join(", ", keys.keySet())
                                     + " (with - in front for the reverse order), was: " + sort);
        }
        return reverse ? order.reversed() : order;
    }

    private static Long parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
