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

import org.apache.camel.util.ObjectHelper;

public final class ThreadHelper {
    public static final String DEFAULT_PATTERN = "Camel Thread ${counter} - ${name}";
    
    private static AtomicLong threadCounter = new AtomicLong();
    
    private ThreadHelper() {
    }
    
    private static long nextThreadCounter() {
        return threadCounter.getAndIncrement();
    }

    /**
     * Creates a new thread name with the given prefix
     *
     * @param pattern the pattern
     * @param name    the name
     * @return the thread name, which is unique
     */
    public static String resolveThreadName(String pattern, String name) {
        if (pattern == null) {
            pattern = DEFAULT_PATTERN;
        }

        // the name could potential have a $ sign we want to keep
        if (name.indexOf("$") > -1) {
            name = name.replaceAll("\\$", "CAMEL_REPLACE_ME");
        }

        // we support ${longName} and ${name} as name placeholders
        String longName = name;
        String shortName = name.contains("?") ? ObjectHelper.before(name, "?") : name;

        String answer = pattern.replaceFirst("\\$\\{counter\\}", "" + nextThreadCounter());
        answer = answer.replaceFirst("\\$\\{longName\\}", longName);
        answer = answer.replaceFirst("\\$\\{name\\}", shortName);
        if (answer.indexOf("$") > -1 || answer.indexOf("${") > -1 || answer.indexOf("}") > -1) {
            throw new IllegalArgumentException("Pattern is invalid: " + pattern);
        }

        if (answer.indexOf("CAMEL_REPLACE_ME") > -1) {
            answer = answer.replaceAll("CAMEL_REPLACE_ME", "\\$");
        }

        return answer;
    }

    
}
