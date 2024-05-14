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

package org.apache.camel.test.junit5.util;

import java.lang.annotation.Annotation;

import org.apache.camel.util.TimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ExtensionHelper {
    private static final Logger LOG = LoggerFactory.getLogger(ExtensionHelper.class);
    public static final String SEPARATOR = "*".repeat(80);

    /**
     * Does the test class have any of the following annotations on the class-level?
     * @return true if has or false otherwise
     */
    public static boolean hasClassAnnotation(Class<?> clazz, String... names) {
        for (String name : names) {
            for (Annotation ann : clazz.getAnnotations()) {
                String annName = ann.annotationType().getName();
                if (annName.equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void testStartHeader(Class<?> testClass, String currentTestName) {
        LOG.info(SEPARATOR);
        LOG.info("Testing: {} ({})", currentTestName, testClass.getName());
        LOG.info(SEPARATOR);
    }

    public static void testEndFooter(Class<?> testClass, String currentTestName, long time) {
        LOG.info(SEPARATOR);
        LOG.info("Testing done: {} ({})", currentTestName, testClass.getName());
        LOG.info("Took: {} ({} millis)", TimeUtils.printDuration(time, true), time);
        LOG.info(SEPARATOR);
    }

    public static void testEndFooter(Class<?> testClass, String currentTestName, long time, RouteCoverageDumperExtension routeCoverageWrapper)
            throws Exception {
        LOG.info(SEPARATOR);
        LOG.info("Testing done: {} ({})", currentTestName, testClass.getName());
        LOG.info("Took: {} ({} millis)", TimeUtils.printDuration(time, true), time);

        if (routeCoverageWrapper != null) {
            routeCoverageWrapper.dumpRouteCoverage(testClass, currentTestName, time);
        }

        LOG.info(SEPARATOR);
    }
}
