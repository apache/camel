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
import java.net.URISyntaxException;
import java.util.function.Consumer;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ExtensionHelper {

    public static final String SEPARATOR = "*".repeat(80);

    private static final Logger LOG = LoggerFactory.getLogger(ExtensionHelper.class);

    private static final String SPRING_BOOT_TEST = "org.springframework.boot.test.context.SpringBootTest";
    private static final String QUARKUS_TEST = "io.quarkus.test.junit.QuarkusTest";
    private static final String CAMEL_QUARKUS_TEST = "org.apache.camel.quarkus.test.CamelQuarkusTest";

    private static void throwUnsupportedClassException(String name) {
        switch (name) {
            case SPRING_BOOT_TEST:
                throw new RuntimeException(
                        "Spring Boot detected: The CamelTestSupport/CamelSpringTestSupport class is not intended for Camel testing with Spring Boot.");
            case QUARKUS_TEST:
            case CAMEL_QUARKUS_TEST: {
                throw new RuntimeException(
                        "Quarkus detected: The CamelTestSupport/CamelSpringTestSupport class is not intended for Camel testing with Quarkus.");
            }
        }

        throw new RuntimeException(
                "Unspecified class detected: The " + name + " class is not intended for Camel testing");
    }

    public static boolean hasUnsupported(Class<?> clazz) {
        hasClassAnnotation(clazz, ExtensionHelper::throwUnsupportedClassException, SPRING_BOOT_TEST, QUARKUS_TEST,
                CAMEL_QUARKUS_TEST);
        return true;
    }

    /**
     * Does the test class have any of the following annotations on the class-level?
     */
    public static void hasClassAnnotation(Class<?> clazz, Consumer<String> classConsumer, String... names) {
        for (String name : names) {
            for (Annotation ann : clazz.getAnnotations()) {
                String annName = ann.annotationType().getName();
                if (annName.equals(name)) {
                    classConsumer.accept(name);
                }
            }
        }
    }

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

    public static void testEndFooter(
            Class<?> testClass, String currentTestName, long time, RouteCoverageDumperExtension routeCoverageWrapper)
            throws Exception {
        LOG.info(SEPARATOR);
        LOG.info("Testing done: {} ({})", currentTestName, testClass.getName());
        LOG.info("Took: {} ({} millis)", TimeUtils.printDuration(time, true), time);

        if (routeCoverageWrapper != null) {
            routeCoverageWrapper.dumpRouteCoverage(testClass, currentTestName, time);
        }

        LOG.info(SEPARATOR);
    }

    public static String normalizeUri(String uri) {
        String n;
        try {
            n = URISupport.normalizeUri(uri);
        } catch (URISyntaxException e) {
            throw RuntimeCamelException.wrapRuntimeException(e);
        }
        return n;
    }
}
