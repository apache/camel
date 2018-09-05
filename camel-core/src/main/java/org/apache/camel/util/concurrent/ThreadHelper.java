/**
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
package org.apache.camel.util.concurrent;

import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.util.StringHelper;

/**
 * Various helper method for thread naming.
 */
public final class ThreadHelper {
    public static final String DEFAULT_PATTERN = "Camel Thread ##counter# - #name#";
    private static final Pattern INVALID_PATTERN = Pattern.compile(".*#\\w+#.*");

    private static AtomicLong threadCounter = new AtomicLong();
    
    private ThreadHelper() {
    }
    
    private static long nextThreadCounter() {
        return threadCounter.getAndIncrement();
    }

    /**
     * Creates a new thread name with the given pattern
     * <p/>
     * See {@link org.apache.camel.spi.ExecutorServiceManager#setThreadNamePattern(String)} for supported patterns.
     *
     * @param pattern the pattern
     * @param name    the name
     * @return the thread name, which is unique
     */
    public static String resolveThreadName(String pattern, String name) {
        if (pattern == null) {
            pattern = DEFAULT_PATTERN;
        }

        // we support #longName# and #name# as name placeholders
        String longName = name;
        String shortName = name.contains("?") ? StringHelper.before(name, "?") : name;
        // must quote the names to have it work as literal replacement
        shortName = Matcher.quoteReplacement(shortName);
        longName = Matcher.quoteReplacement(longName);

        // replace tokens
        String answer = pattern.replaceFirst("#counter#", "" + nextThreadCounter());
        answer = answer.replaceFirst("#longName#", longName);
        answer = answer.replaceFirst("#name#", shortName);

        // are there any #word# combos left, if so they should be considered invalid tokens
        if (INVALID_PATTERN.matcher(answer).matches()) {
            throw new IllegalArgumentException("Pattern is invalid: " + pattern);
        }

        return answer;
    }

}
