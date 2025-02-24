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
package org.apache.camel.spring.scan;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.apache.camel.core.xml.PatternBasedPackageScanFilter;
import org.junit.jupiter.api.BeforeEach;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class ScanTestSupport {

    protected PatternBasedPackageScanFilter filter;

    @BeforeEach
    public void setUp() throws Exception {
        filter = new PatternBasedPackageScanFilter();
    }

    protected void validateMatchingSetContains(Set<Class<?>> scannedClasses, Class<?>... matchingClasses) {
        HashSet<Class<?>> expectedSet = new HashSet<>(Arrays.asList(matchingClasses));
        validateMatchingSetContains(scannedClasses, expectedSet);
    }

    protected void validateMatchingSetContains(Set<Class<?>> scannedClasses, Set<Class<?>> matchingClasses) {
        Set<Class<?>> matching = getMatchingClasses(scannedClasses, filter);
        assertEquals(matchingClasses.size(), matching.size(), "Incorrect number of classes matched");

        for (Class<?> expected : matchingClasses) {
            assertTrue(matching.contains(expected), "Expected matching class '" + expected + "' is not present");
        }
    }

    protected void addIncludePatterns(String... patterns) {
        for (String pattern : patterns) {
            filter.addIncludePattern(pattern);
        }
    }

    protected void addExcludePatterns(String... patterns) {
        for (String pattern : patterns) {
            filter.addExcludePattern(pattern);
        }
    }

    public Set<Class<?>> getMatchingClasses(Set<Class<?>> scannedClasses, PatternBasedPackageScanFilter filter) {
        Set<Class<?>> matching = new HashSet<>();

        for (Class<?> candidate : scannedClasses) {
            if (filter.matches(candidate)) {
                matching.add(candidate);
            }
        }

        return matching;
    }
}
