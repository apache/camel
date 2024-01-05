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
package org.apache.camel.test.junit5;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.Predicate;
import org.apache.camel.Route;
import org.apache.camel.builder.Builder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.ValueBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.Debugger;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.PredicateAssertHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Provides utility methods for camel test purpose (builders, assertions, endpoint resolutions, file helpers).
 */
public final class TestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(TestSupport.class);

    private TestSupport() {
    }

    // -------------------------------------------------------------------------
    // Builder methods for expressions used when testing
    // -------------------------------------------------------------------------

    /**
     * Returns a value builder for the given header.
     */
    public static ValueBuilder header(String name) {
        return Builder.header(name);
    }

    /**
     * Returns a value builder for the given exchange property.
     */
    public static ValueBuilder exchangeProperty(String name) {
        return Builder.exchangeProperty(name);
    }

    /**
     * Returns a predicate and value builder for the inbound body on an exchange.
     */
    public static ValueBuilder body() {
        return Builder.body();
    }

    /**
     * Returns a predicate and value builder for the inbound message body as a specific type.
     */
    public static <T> ValueBuilder bodyAs(Class<T> type) {
        return Builder.bodyAs(type);
    }

    /**
     * Returns a value builder for the given system property.
     */
    public static ValueBuilder systemProperty(String name) {
        return Builder.systemProperty(name);
    }

    /**
     * Returns a value builder for the given system property.
     */
    public static ValueBuilder systemProperty(String name, String defaultValue) {
        return Builder.systemProperty(name, defaultValue);
    }

    // -----------------------------------------------------------------------
    // Assertions
    // -----------------------------------------------------------------------

    /**
     * Asserts that a given value is of an expected type.
     */
    public static <T> T assertIsInstanceOf(Class<T> expectedType, Object value) {
        assertNotNull(value, "Expected an instance of type: " + expectedType.getName() + " but was null");
        assertTrue(expectedType.isInstance(value), "Object should be of type " + expectedType.getName() + " but was: " + value
                                                   + " with the type: " + value.getClass().getName());
        return expectedType.cast(value);
    }

    /**
     * Asserts that a given endpoint has an expected uri.
     */
    public static void assertEndpointUri(Endpoint endpoint, String expectedUri) {
        assertNotNull(endpoint, "Endpoint is null when expecting endpoint for: " + expectedUri);
        assertEquals(expectedUri, endpoint.getEndpointUri(), "Endpoint uri for: " + endpoint);
    }

    /**
     * Asserts that the In message on the exchange contains an header with a given name and expected value.
     */
    public static Object assertInMessageHeader(Exchange exchange, String headerName, Object expectedValue) {
        return assertMessageHeader(exchange.getIn(), headerName, expectedValue);
    }

    /**
     * Asserts that the message on the exchange contains an header with a given name and expected value.
     */
    public static Object assertOutMessageHeader(Exchange exchange, String headerName, Object expectedValue) {
        return assertMessageHeader(exchange.getMessage(), headerName, expectedValue);
    }

    /**
     * Asserts that the given exchange has a given expectedBody on the IN message.
     */
    public static void assertInMessageBodyEquals(Exchange exchange, Object expectedBody) throws InvalidPayloadException {
        assertNotNull(exchange, "Should have a response exchange");

        Object actualBody;
        if (expectedBody == null) {
            actualBody = exchange.getIn().getMandatoryBody();
            assertEquals(expectedBody, actualBody, "in body of: " + exchange);
        } else {
            actualBody = exchange.getIn().getMandatoryBody(expectedBody.getClass());
        }
        assertEquals(expectedBody, actualBody, "in body of: " + exchange);

        LOG.debug("Received response: {} with in: {}", exchange, exchange.getIn());
    }

    /**
     * Asserts that the given exchange has a given expectedBody on the message.
     */
    public static void assertMessageBodyEquals(Exchange exchange, Object expectedBody) throws InvalidPayloadException {
        assertNotNull(exchange, "Should have a response exchange!");

        Object actualBody;
        if (expectedBody == null) {
            actualBody = exchange.getMessage().getMandatoryBody();
            assertEquals(expectedBody, actualBody, "output body of: " + exchange);
        } else {
            actualBody = exchange.getMessage().getMandatoryBody(expectedBody.getClass());
        }
        assertEquals(expectedBody, actualBody, "output body of: " + exchange);

        LOG.debug("Received response: {} with out: {}", exchange, exchange.getMessage());
    }

    /**
     * Asserts that a given message contains an header with a given name and expected value.
     */
    public static Object assertMessageHeader(Message message, String headerName, Object expectedValue) {
        Object actualValue = message.getHeader(headerName);
        assertEquals(expectedValue, actualValue, "Header: " + headerName + " on Message: " + message);
        return actualValue;
    }

    /**
     * Asserts that the given expression when evaluated returns the given answer.
     */
    public static Object assertExpression(Expression expression, Exchange exchange, Object expectedAnswer) {
        Object actualAnswer;
        if (expectedAnswer != null) {
            actualAnswer = expression.evaluate(exchange, expectedAnswer.getClass());
        } else {
            actualAnswer = expression.evaluate(exchange, Object.class);
        }

        LOG.debug("Evaluated expression: {} on exchange: {} result: {}", expression, exchange, actualAnswer);

        assertEquals(expectedAnswer, actualAnswer, "Expression: " + expression + " on Exchange: " + exchange);
        return actualAnswer;
    }

    /**
     * Asserts that a given predicate returns <code>true</code> on a given exchange.
     */
    public static void assertPredicateMatches(Predicate predicate, Exchange exchange) {
        assertPredicate(predicate, exchange, true);
    }

    /**
     * Asserts that a given predicate returns <code>false</code> on a given exchange.
     */
    public static void assertPredicateDoesNotMatch(Predicate predicate, Exchange exchange) {
        try {
            PredicateAssertHelper.assertMatches(predicate, "Predicate should match: ", exchange);
        } catch (AssertionError e) {
            LOG.debug("Caught expected assertion error: {}", e.getMessage(), e);
        }
        assertPredicate(predicate, exchange, false);
    }

    /**
     * Asserts that the predicate returns the expected value on the exchange.
     */
    public static boolean assertPredicate(final Predicate predicate, Exchange exchange, boolean expectedValue) {
        if (expectedValue) {
            PredicateAssertHelper.assertMatches(predicate, "Predicate failed: ", exchange);
        }
        boolean actualValue = predicate.matches(exchange);

        LOG.debug("Evaluated predicate: {} on exchange: {} result: {}", predicate, exchange, actualValue);

        assertEquals(expectedValue, actualValue, "Predicate: " + predicate + " on Exchange: " + exchange);
        return actualValue;
    }

    /**
     * Asserts that a given list has a single element.
     */
    public static <T> T assertOneElement(List<T> list) {
        assertEquals(1, list.size(), "Size of list should be 1: " + list);
        return list.get(0);
    }

    /**
     * Asserts that a given list has a given expected size.
     */
    public static <T> List<T> assertListSize(List<T> list, int expectedSize) {
        return assertListSize("List", list, expectedSize);
    }

    /**
     * Asserts that a list is of the given size. When the assertion is broken, the error message starts with a given
     * prefix.
     */
    public static <T> List<T> assertListSize(String brokenAssertionMessagePrefix, List<T> list, int expectedSize) {
        assertEquals(expectedSize, list.size(),
                brokenAssertionMessagePrefix + " should be of size: " + expectedSize + " but is: " + list);
        return list;
    }

    /**
     * Asserts that a given collection has a given size.
     */
    public static <T> Collection<T> assertCollectionSize(Collection<T> list, int expectedSize) {
        return assertCollectionSize("List", list, expectedSize);
    }

    /**
     * Asserts that a given collection has a given size. When the assertion is broken, the error message starts with a
     * given prefix.
     */
    public static <
            T> Collection<T> assertCollectionSize(String brokenAssertionMessagePrefix, Collection<T> list, int expectedSize) {
        assertEquals(expectedSize, list.size(),
                brokenAssertionMessagePrefix + " should be of size: " + expectedSize + " but is: " + list);
        return list;
    }

    /**
     * Asserts that the text contains the given string.
     *
     * @param text          the text to compare
     * @param containedText the text which must be contained inside the other text parameter
     */
    public static void assertStringContains(String text, String containedText) {
        assertNotNull(text, "Text should not be null!");
        assertTrue(text.contains(containedText), "Text: " + text + " does not contain: " + containedText);
    }

    /**
     * Asserts that two given directories are equal. To be used for folder/directory comparison that works across
     * different platforms such as Window, Mac and Linux.
     */
    public static void assertDirectoryEquals(String expected, String actual) {
        assertDirectoryEquals(null, expected, actual);
    }

    /**
     * Asserts that two given directories are equal. To be used for folder/directory comparison that works across
     * different platforms such as Window, Mac and Linux.
     */
    public static void assertDirectoryEquals(String message, String expected, String actual) {
        // must use single / as path separators
        String expectedPath = expected.replace('\\', '/');
        String actualPath = actual.replace('\\', '/');

        if (message != null) {
            assertEquals(message, expectedPath, actualPath);
        } else {
            assertEquals(expectedPath, actualPath);
        }
    }

    /**
     * To be used to check is a directory is found in the file system
     */
    public static void assertDirectoryExists(Path file) {
        assertTrue(Files.exists(file), "Directory " + file + " should exist");
        assertTrue(Files.isDirectory(file), "Directory " + file + " should be a directory");
    }

    /**
     * Asserts that a given directory is found in the file system.
     */
    public static void assertDirectoryExists(String filename) {
        File file = new File(filename);
        assertTrue(file.exists(), "Directory " + filename + " should exist");
        assertTrue(file.isDirectory(), "Directory " + filename + " should be a directory");
    }

    /**
     * To be used to check is a file is found in the file system
     */
    public static void assertFileExists(Path file) {
        assertTrue(Files.exists(file), "File " + file + " should exist");
        assertTrue(Files.exists(file), "File " + file + " should be a file");
    }

    /**
     * To be used to check is a file is found in the file system
     */
    public static void assertFileExists(Path file, String content) throws IOException {
        assertTrue(Files.exists(file), "File " + file + " should exist");
        assertTrue(Files.isRegularFile(file), "File " + file + " should be a file");
        assertEquals(content, new String(Files.readAllBytes(file)), "File " + file + " has unexpected content");
    }

    /**
     * Asserts that a given file is found in the file system.
     */
    public static void assertFileExists(String filename) {
        File file = new File(filename);
        assertTrue(file.exists(), "File " + filename + " should exist");
        assertTrue(file.isFile(), "File " + filename + " should be a file");
    }

    /**
     * To be used to check is a file is <b>not</b> found in the file system
     */
    public static void assertFileNotExists(Path file) {
        assertFalse(Files.exists(file), "File " + file + " should not exist");
    }

    /**
     * Asserts that a given file is <b>not</b> found in the file system.
     */
    public static void assertFileNotExists(String filename) {
        File file = new File(filename);
        assertFalse(file.exists(), "File " + filename + " should not exist");
    }

    // -----------------------------------------------------------------------
    // Other helpers, resolution, file, getRouteList
    // -----------------------------------------------------------------------

    /**
     * Resolves an endpoint and asserts that it is found.
     */
    public static Endpoint resolveMandatoryEndpoint(CamelContext context, String endpointUri) {
        Endpoint endpoint = context.getEndpoint(endpointUri);

        assertNotNull(endpoint, "No endpoint found for URI: " + endpointUri);

        return endpoint;
    }

    /**
     * Resolves an endpoint and asserts that it is found.
     */
    public static <
            T extends Endpoint> T resolveMandatoryEndpoint(CamelContext context, String endpointUri, Class<T> endpointType) {
        T endpoint = context.getEndpoint(endpointUri, endpointType);

        assertNotNull(endpoint, "No endpoint found for URI: " + endpointUri);

        return endpoint;
    }

    /**
     * Creates an exchange with the given body.
     */
    public static Exchange createExchangeWithBody(CamelContext camelContext, Object body) {
        Exchange exchange = new DefaultExchange(camelContext);
        Message message = exchange.getIn();
        message.setBody(body);
        return exchange;
    }

    /**
     * A helper method to create a list of Route objects for a given route builder.
     */
    public static List<Route> getRouteList(RouteBuilder builder) throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.addRoutes(builder);
        context.start();
        List<Route> answer = context.getRoutes();
        context.stop();
        return answer;
    }

    /**
     * Recursively delete a directory, useful to zapping test data. Deletion will be attempted up to five time before
     * giving up.
     *
     * @param  file the directory to be deleted
     * @return      <tt>false</tt> when an error occur while deleting directory
     */
    public static boolean deleteDirectory(Path file) {
        return deleteDirectory(file.toFile());
    }

    /**
     * Recursively delete a directory, useful to zapping test data. Deletion will be attempted up to five time before
     * giving up.
     *
     * @param  file the directory to be deleted
     * @return      <tt>false</tt> when an error occur while deleting directory
     */
    public static boolean deleteDirectory(String file) {
        return deleteDirectory(new File(file));
    }

    /**
     * Recursively delete a directory, useful to zapping test data. Deletion will be attempted up to five time before
     * giving up.
     *
     * @param  file the directory to be deleted
     * @return      <tt>false</tt> when an error occur while deleting directory
     */
    public static boolean deleteDirectory(File file) {
        int tries = 0;
        int maxTries = 5;
        boolean exists = true;
        while (exists && tries < maxTries) {
            recursivelyDeleteDirectory(file);
            tries++;
            exists = file.exists();
            if (exists) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        return !exists;
    }

    /**
     * Recursively delete a directory. Deletion will be attempted a single time before giving up.
     *
     * @param file the directory to be deleted
     */
    public static void recursivelyDeleteDirectory(File file) {
        if (!file.exists()) {
            return;
        }

        if (file.isDirectory()) {
            File[] files = file.listFiles();
            for (File child : files) {
                recursivelyDeleteDirectory(child);
            }
        }
        try {
            Files.delete(file.toPath());
        } catch (IOException e) {
            LOG.warn("Deletion of file: {} failed", file.getAbsolutePath());
        }
    }

    /**
     * Creates a given directory.
     *
     * @param file the directory to be created
     */
    public static void createCleanDirectory(Path file) {
        deleteDirectory(file);
        createDirectory(file);
    }

    /**
     * Creates a given directory.
     *
     * @param file the directory to be created
     */
    public static void createDirectory(Path file) {
        file.toFile().mkdirs();
    }

    /**
     * Creates a given directory.
     *
     * @param file the directory to be created
     */
    public static void createDirectory(String file) {
        File dir = new File(file);
        dir.mkdirs();
    }

    /**
     * Tells whether the current Operating System is the given expected platform.
     * <p/>
     * Uses <tt>os.name</tt> from the system properties to determine the Operating System.
     *
     * @param  expectedPlatform such as Windows
     * @return                  <tt>true</tt> when the current Operating System is the expected platform, <tt>false</tt>
     *                          otherwise.
     */
    public static boolean isPlatform(String expectedPlatform) {
        String osName = System.getProperty("os.name").toLowerCase(Locale.US);
        return osName.contains(expectedPlatform.toLowerCase(Locale.US));
    }

    /**
     * Tells whether the current Java Virtual Machine has been issued by a given expected vendor.
     * <p/>
     * Uses <tt>java.vendor</tt> from the system properties to determine the vendor.
     *
     * @param  expectedVendor such as IBM
     * @return                <tt>true</tt> when the current Java Virtual Machine has been issued by the expected
     *                        vendor, <tt>false</tt> otherwise.
     */
    public static boolean isJavaVendor(String expectedVendor) {
        String javaVendor = System.getProperty("java.vendor").toLowerCase(Locale.US);
        return javaVendor.contains(expectedVendor.toLowerCase(Locale.US));
    }

    /**
     * Returns the current major Java version e.g 8.
     * <p/>
     * Uses <tt>java.specification.version</tt> from the system properties to determine the major version.
     *
     * @return the current major Java version.
     */
    public static int getJavaMajorVersion() {
        String javaSpecVersion = System.getProperty("java.specification.version");
        return Integer.parseInt(javaSpecVersion);
    }

    /**
     * Indicates whether the component {@code camel-debug} is present in the classpath of the test.
     *
     * @return {@code true} if it is present, {@code false} otherwise.
     */
    public static boolean isCamelDebugPresent() {
        // Needs to be detected before initializing and starting the camel context
        return Thread.currentThread()
                .getContextClassLoader()
                .getResource(String.format("%s%s", FactoryFinder.DEFAULT_PATH, Debugger.FACTORY))
               != null;
    }

    public static String fileUri(Path testDirectory) {
        return "file:" + testDirectory;
    }

    public static String fileUri(Path testDirectory, String query) {
        return "file:" + testDirectory + (query.startsWith("?") ? "" : "/") + query;
    }

    public static void executeSlowly(int count, long interval, TimeUnit timeUnit, IntConsumer task) throws Exception {
        for (int i = 0; i < count; i++) {
            task.accept(i);
            Thread.sleep(timeUnit.toMillis(interval));
        }
    }
}
