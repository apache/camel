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
package org.apache.camel;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import org.apache.camel.builder.Builder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.ValueBuilder;
import org.apache.camel.component.seda.SedaComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.processor.Pipeline;
import org.apache.camel.processor.errorhandler.ErrorHandlerSupport;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.PredicateAssertHelper;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A bunch of useful testing methods
 */
@ResourceLock(value = Resources.SYSTEM_PROPERTIES, mode = ResourceAccessMode.READ)
public abstract class TestSupport {

    protected static final String LS = System.lineSeparator();
    private static final Logger LOG = LoggerFactory.getLogger(TestSupport.class);

    protected TestInfo info;
    protected boolean testDirectoryCleaned;

    protected Logger log = LoggerFactory.getLogger(getClass());

    @Override
    public String toString() {
        return getName() + "(" + getClass().getName() + ")";
    }

    public String getName() {
        return info.getTestMethod().map(Method::getName).orElse("");
    }

    @BeforeEach
    public void setTestInfo(TestInfo info) {
        this.info = info;
    }

    @BeforeEach
    public void setUp() throws Exception {
        Assumptions.assumeTrue(canRunOnThisPlatform());
        deleteTestDirectory();
    }

    public void deleteTestDirectory() {
        if (!testDirectoryCleaned) {
            deleteDirectory(testDirectory().toFile());
            testDirectoryCleaned = true;
        }
    }

    @AfterEach
    public void tearDown() throws Exception {
        // make sure we cleanup the platform mbean server
        TestSupportJmxCleanup.removeMBeans(null);
        testDirectoryCleaned = false;
    }

    protected Path testDirectory() {
        return testDirectory(false);
    }

    protected Path testDirectory(boolean create) {
        Class<?> testClass = getClass();
        if (create) {
            deleteTestDirectory();
        }
        return testDirectory(testClass, create);
    }

    public static Path testDirectory(Class<?> testClass, boolean create) {
        Path dir = Paths.get("target", "data", testClass.getSimpleName());
        if (create) {
            try {
                Files.createDirectories(dir);
            } catch (IOException e) {
                throw new IllegalStateException("Unable to create test directory: " + dir, e);
            }
        }
        return dir;
    }

    protected Path testFile(String dir) {
        return testDirectory().resolve(dir);
    }

    protected Path testDirectory(String dir) {
        return testDirectory(dir, false);
    }

    protected Path testDirectory(String dir, boolean create) {
        Path f = testDirectory().resolve(dir);
        if (create) {
            try {
                Files.createDirectories(f);
            } catch (IOException e) {
                throw new IllegalStateException("Unable to create test directory: " + dir, e);
            }
        }
        return f;
    }

    protected String fileUri() {
        return "file:" + testDirectory();
    }

    protected String fileUri(String query) {
        return "file:" + testDirectory() + (query.startsWith("?") ? "" : "/") + query;
    }

    protected boolean canRunOnThisPlatform() {
        return true;
    }

    // Builder methods for expressions used when testing
    // -------------------------------------------------------------------------

    /**
     * Returns a value builder for the given header
     */
    public static ValueBuilder header(String name) {
        return Builder.header(name);
    }

    /**
     * Returns a value builder for the given exchange property
     */
    public static ValueBuilder exchangeProperty(String name) {
        return Builder.exchangeProperty(name);
    }

    /**
     * Returns a predicate and value builder for the inbound body on an exchange
     */
    public static ValueBuilder body() {
        return init(Builder.body());
    }

    /**
     * Returns a predicate and value builder for the inbound message body as a specific type
     */
    public static <T> ValueBuilder bodyAs(Class<T> type) {
        return Builder.bodyAs(type);
    }

    /**
     * Returns a value builder for the given system property
     */
    public static ValueBuilder systemProperty(String name) {
        return Builder.systemProperty(name);
    }

    /**
     * Returns a value builder for the given system property
     */
    public static ValueBuilder systemProperty(String name, String defaultValue) {
        return Builder.systemProperty(name, defaultValue);
    }

    // Assertions
    // -----------------------------------------------------------------------

    public static <T> T assertIsInstanceOf(Class<T> expectedType, Object value) {
        assertNotNull(value, "Expected an instance of type: " + expectedType.getName() + " but was null");
        assertTrue(expectedType.isInstance(value), "object should be a " + expectedType.getName() + " but was: " + value
                                                   + " with type: " + value.getClass().getName());
        return expectedType.cast(value);
    }

    public static void assertEndpointUri(Endpoint endpoint, String uri) {
        assertNotNull(endpoint, "Endpoint is null when expecting endpoint for: " + uri);
        assertEquals(uri, endpoint.getEndpointUri(), "Endoint uri for: " + endpoint);
    }

    /**
     * Asserts the In message on the exchange contains the expected value
     */
    public static Object assertInMessageHeader(Exchange exchange, String name, Object expected) {
        return assertMessageHeader(exchange.getIn(), name, expected);
    }

    /**
     * Asserts that the given exchange has an OUT message of the given body value
     *
     * @param  exchange                the exchange which should have an OUT message
     * @param  expected                the expected value of the OUT message
     * @throws InvalidPayloadException is thrown if the payload is not the expected class type
     */
    public static void assertInMessageBodyEquals(Exchange exchange, Object expected) throws InvalidPayloadException {
        assertNotNull(exchange, "Should have a response exchange!");

        Object actual;
        if (expected == null) {
            actual = exchange.getIn().getMandatoryBody();
            assertEquals(expected, actual, "in body of: " + exchange);
        } else {
            actual = exchange.getIn().getMandatoryBody(expected.getClass());
        }
        assertEquals(expected, actual, "in body of: " + exchange);

        LOG.debug("Received response: {} with in: {}", exchange, exchange.getIn());
    }

    public static Object assertMessageHeader(Message message, String name, Object expected) {
        Object value = message.getHeader(name);
        assertEquals(expected, value, "Header: " + name + " on Message: " + message);
        return value;
    }

    public static Object assertProperty(Exchange exchange, String name, Object expected) {
        Object value = exchange.getProperty(name);
        assertEquals(expected, value, "Property: " + name + " on Exchange: " + exchange);
        return value;
    }

    /**
     * Asserts that the given expression when evaluated returns the given answer
     */
    public static Object assertExpression(Expression expression, Exchange exchange, Object expected) {
        expression.init(exchange.getContext());

        Object value;
        if (expected != null) {
            value = expression.evaluate(exchange, expected.getClass());
        } else {
            value = expression.evaluate(exchange, Object.class);
        }

        LOG.debug("Evaluated expression: {} on exchange: {} result: {}", expression, exchange, value);

        assertEquals(expected, value, "Expression: " + expression + " on Exchange: " + exchange);
        return value;
    }

    /**
     * Asserts that the predicate returns the expected value on the exchange
     */
    public static void assertPredicateMatches(Predicate predicate, Exchange exchange) {
        assertPredicate(predicate, exchange, true);
    }

    /**
     * Asserts that the predicate returns the expected value on the exchange
     */
    public static void assertPredicateDoesNotMatch(Predicate predicate, Exchange exchange) {
        predicate.init(exchange.getContext());
        try {
            PredicateAssertHelper.assertMatches(predicate, "Predicate should match: ", exchange);
        } catch (AssertionError e) {
            LOG.debug("Caught expected assertion error: {}", e.getMessage(), e);
        }
        assertPredicate(predicate, exchange, false);
    }

    /**
     * Asserts that the predicate returns the expected value on the exchange
     */
    public static boolean assertPredicate(final Predicate predicate, Exchange exchange, boolean expected) {
        predicate.init(exchange.getContext());

        if (expected) {
            PredicateAssertHelper.assertMatches(predicate, "Predicate failed: ", exchange);
        }
        boolean value = predicate.matches(exchange);

        LOG.debug("Evaluated predicate: {} on exchange: {} result: {}", predicate, exchange, value);

        assertEquals(expected, value, "Predicate: " + predicate + " on Exchange: " + exchange);
        return value;
    }

    /**
     * Resolves an endpoint and asserts that it is found
     */
    public static Endpoint resolveMandatoryEndpoint(CamelContext context, String uri) {
        Endpoint endpoint = context.getEndpoint(uri);

        assertNotNull(endpoint, "No endpoint found for URI: " + uri);

        return endpoint;
    }

    /**
     * Resolves an endpoint and asserts that it is found
     */
    public static <T extends Endpoint> T resolveMandatoryEndpoint(CamelContext context, String uri, Class<T> endpointType) {
        T endpoint = context.getEndpoint(uri, endpointType);

        assertNotNull(endpoint, "No endpoint found for URI: " + uri);

        return endpoint;
    }

    /**
     * Creates an exchange with the given body
     */
    protected Exchange createExchangeWithBody(CamelContext camelContext, Object body) {
        Exchange exchange = new DefaultExchange(camelContext);
        Message message = exchange.getIn();
        message.setHeader("testName", getName());
        message.setHeader("testClass", getClass().getName());
        message.setBody(body);
        return exchange;
    }

    public static <T> T assertOneElement(List<T> list) {
        assertEquals(1, list.size(), "Size of list should be 1: " + list);
        return list.get(0);
    }

    /**
     * Asserts that a list is of the given size
     */
    public static <T> List<T> assertListSize(List<T> list, int size) {
        return assertListSize("List", list, size);
    }

    /**
     * Asserts that a list is of the given size
     */
    public static <T> List<T> assertListSize(String message, List<T> list, int size) {
        assertEquals(size, list.size(), message + " should be of size: " + size + " but is: " + list);
        return list;
    }

    /**
     * Asserts that a list is of the given size
     */
    public static <T> Collection<T> assertCollectionSize(Collection<T> list, int size) {
        return assertCollectionSize("List", list, size);
    }

    /**
     * Asserts that a list is of the given size
     */
    public static <T> Collection<T> assertCollectionSize(String message, Collection<T> list, int size) {
        assertEquals(size, list.size(), message + " should be of size: " + size + " but is: " + list);
        return list;
    }

    /**
     * A helper method to create a list of Route objects for a given route builder
     */
    public static List<Route> getRouteList(RouteBuilder builder) throws Exception {
        CamelContext context = new DefaultCamelContext();

        // make SEDA run faster
        context.getComponent("seda", SedaComponent.class).setDefaultPollTimeout(10);

        context.addRoutes(builder);
        context.start();
        List<Route> answer = context.getRoutes();
        context.stop();
        return answer;
    }

    /**
     * Asserts that the text contains the given string
     *
     * @param text          the text to compare
     * @param containedText the text which must be contained inside the other text parameter
     */
    public static void assertStringContains(String text, String containedText) {
        assertNotNull(text, "Text should not be null!");
        assertTrue(text.contains(containedText), "Text: " + text + " does not contain: " + containedText);
    }

    /**
     * If a processor is wrapped with a bunch of DelegateProcessor or DelegateAsyncProcessor objects this call will
     * drill through them and return the wrapped Processor.
     */
    public static Processor unwrap(Processor processor) {
        while (true) {
            if (processor instanceof DelegateProcessor) {
                processor = ((DelegateProcessor) processor).getProcessor();
            } else {
                return processor;
            }
        }
    }

    /**
     * If a processor is wrapped with a bunch of DelegateProcessor or DelegateAsyncProcessor objects this call will
     * drill through them and return the Channel.
     * <p/>
     * Returns null if no channel is found.
     */
    public static Channel unwrapChannel(Processor processor) {
        while (true) {
            if (processor instanceof Pipeline) {
                processor = ((Pipeline) processor).next().get(0);
            }
            if (processor instanceof Channel) {
                return (Channel) processor;
            } else if (processor instanceof DelegateProcessor) {
                processor = ((DelegateProcessor) processor).getProcessor();
            } else if (processor instanceof ErrorHandlerSupport) {
                processor = ((ErrorHandlerSupport) processor).getOutput();
            } else {
                return null;
            }
        }
    }

    /**
     * Recursively delete a directory, useful to zapping test data
     *
     * @param file the directory to be deleted
     */
    public static void deleteDirectory(String file) {
        deleteDirectory(new File(file));
    }

    /**
     * Recursively delete a directory, useful to zapping test data
     *
     * @param file the directory to be deleted
     */
    public static void deleteDirectory(File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File child : files) {
                    deleteDirectory(child);
                }
            }
        }

        file.delete();
    }

    /**
     * create the directory
     *
     * @param file the directory to be created
     */
    public static void createDirectory(String file) {
        File dir = new File(file);
        dir.mkdirs();
    }

    /**
     * To be used for folder/directory comparison that works across different platforms such as Window, Mac and Linux.
     */
    public static void assertDirectoryEquals(String expected, String actual) {
        assertDirectoryEquals(null, expected, actual);
    }

    /**
     * To be used for folder/directory comparison that works across different platforms such as Window, Mac and Linux.
     */
    public static void assertDirectoryEquals(String message, String expected, String actual) {
        // must use single / as path separators
        String expectedPath = expected.replace('\\', '/');
        String actualPath = actual.replace('\\', '/');

        if (message != null) {
            assertEquals(expectedPath, actualPath, message);
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
     * To be used to check is a directory is found in the file system
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
     * To be used to check is a file is found in the file system
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
     * To be used to check is a file is <b>not</b> found in the file system
     */
    public static void assertFileNotExists(String filename) {
        File file = new File(filename);
        assertFalse(file.exists(), "File " + filename + " should not exist");
    }

    /**
     * Is this OS the given platform.
     * <p/>
     * Uses <tt>os.name</tt> from the system properties to determine the OS.
     *
     * @param  platform such as Windows
     * @return          <tt>true</tt> if its that platform.
     */
    public static boolean isPlatform(String platform) {
        String osName = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
        return osName.contains(platform.toLowerCase(Locale.ENGLISH));
    }

    /**
     * Is this Java by the given vendor.
     * <p/>
     * Uses <tt>java.vendor</tt> from the system properties to determine the vendor.
     *
     * @param  vendor such as IBM
     * @return        <tt>true</tt> if its that vendor.
     */
    public static boolean isJavaVendor(String vendor) {
        String javaVendor = System.getProperty("java.vendor").toLowerCase(Locale.ENGLISH);
        return javaVendor.contains(vendor.toLowerCase(Locale.ENGLISH));
    }

    /**
     * Is this version the given Java version.
     * <p/>
     * Uses <tt>java.version</tt> from the system properties to determine the version.
     *
     * @param  version such as 17
     * @return         <tt>true</tt> if its that vendor.
     */
    public static boolean isJavaVersion(String version) {
        return Integer.parseInt(version) == getJavaMajorVersion();
    }

    /**
     * Returns the current major Java version e.g 17.
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
     * Used for registering a sysetem property.
     * <p/>
     * if the property already contains the passed value nothing will happen. If the system property has already a
     * value, the passed value will be appended separated by <tt>separator</tt>
     *
     * @param sysPropertyName  the name of the system property to be set
     * @param sysPropertyValue the value to be set for the system property passed as sysPropertyName
     * @param separator        the property separator to be used to append sysPropertyValue
     */
    public static void registerSystemProperty(String sysPropertyName, String sysPropertyValue, String separator) {
        synchronized (System.getProperties()) {
            if (System.getProperties().contains(sysPropertyName)) {
                String current = System.getProperty(sysPropertyName);
                if (!current.contains(sysPropertyValue)) {
                    System.setProperty(sysPropertyName, current + separator + sysPropertyValue);
                }
            } else {
                System.setProperty(sysPropertyName, sysPropertyValue);
            }
        }
    }

    private static ValueBuilder init(ValueBuilder builder) {
        Expression exp = builder.getExpression();
        if (exp != null) {
            exp.init(new DefaultCamelContext());
        }
        return builder;
    }

    public static <T> void assumeThat(String s, T t, Matcher<T> m) {
        Assumptions.assumeTrue(m.matches(t), s);
    }

}
