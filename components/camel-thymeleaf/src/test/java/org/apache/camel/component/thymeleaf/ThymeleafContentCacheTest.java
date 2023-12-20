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
package org.apache.camel.component.thymeleaf;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static java.util.Map.entry;

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
public class ThymeleafContentCacheTest extends ThymeleafAbstractBaseTest {

    @Override
    public boolean useJmx() {

        return true;
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {

        super.setUp();

        // create a template file in the classpath
        template.sendBodyAndHeader(
                "file://target/test-classes/org/apache/camel/component/thymeleaf?fileExist=Override",
                stringTemplate(), Exchange.FILE_NAME, "letter-cached.html");
    }

    @Test
    public void testNotCached() throws Exception {

        MockEndpoint mock = getMockEndpoint(MOCK_RESULT);
        mock.expectedBodiesReceived(expected());

        Map<String, Object> headerMap = new HashMap<>(
                Map.ofEntries(entry(LAST_NAME, "Doe"), entry(FIRST_NAME, JANE), entry(ITEM, "Widgets for Dummies")));

        template.sendBodyAndHeaders(DIRECT_START_NO_CACHE, SPAZZ_TESTING_SERVICE, headerMap);
        mock.assertIsSatisfied();

        // now change content in the file in the classpath and try again
        template.sendBodyAndHeader("file://target/test-classes/org/apache/camel/component/thymeleaf?fileExist=Override",
                "not-cached\n" + stringTemplate(), Exchange.FILE_NAME, "letter-cached.html");

        mock.reset();
        mock.expectedBodiesReceived(expectedNoCache());

        template.sendBodyAndHeaders(DIRECT_START_NO_CACHE, SPAZZ_TESTING_SERVICE, headerMap);
        mock.assertIsSatisfied();
    }

    @Test
    public void testCached() throws Exception {

        MockEndpoint mock = getMockEndpoint(MOCK_RESULT);
        mock.expectedBodiesReceived(expected());

        Map<String, Object> headerMap = new HashMap<>(
                Map.ofEntries(entry(LAST_NAME, "Doe"), entry(FIRST_NAME, JANE), entry(ITEM, "Widgets for Dummies")));

        template.sendBodyAndHeaders(DIRECT_START_WITH_CACHE, SPAZZ_TESTING_SERVICE, headerMap);
        mock.assertIsSatisfied();

        // now change content in the file in the classpath and try again
        template.sendBodyAndHeader("file://target/test-classes/org/apache/camel/component/thymeleaf?fileExist=Override",
                "not-cached\n" + stringTemplate(), Exchange.FILE_NAME, "letter-cached.html");

        mock.reset();
        // we must expect the original file content since caching is enabled
        mock.expectedBodiesReceived(expected());

        template.sendBodyAndHeaders(DIRECT_START_WITH_CACHE, SPAZZ_TESTING_SERVICE, headerMap);
        mock.assertIsSatisfied();
    }

    @Test
    public void testCachedWithDelay() throws Exception {

        MockEndpoint mock = getMockEndpoint(MOCK_RESULT);
        mock.expectedBodiesReceived(expected());

        Map<String, Object> headerMap = new HashMap<>(
                Map.ofEntries(entry(LAST_NAME, "Doe"), entry(FIRST_NAME, JANE), entry(ITEM, "Widgets for Dummies")));

        template.sendBodyAndHeaders(DIRECT_START_CACHE_TTL, SPAZZ_TESTING_SERVICE, headerMap);
        mock.assertIsSatisfied();

        // now change content in the file in the classpath and try again .... with no delay
        template.sendBodyAndHeader("file://target/test-classes/org/apache/camel/component/thymeleaf?fileExist=Override",
                "not-cached\n" + stringTemplate(), Exchange.FILE_NAME, "letter-cached.html");

        mock.reset();
        // we must expect the original file content since caching is enabled
        mock.expectedBodiesReceived(expected());

        template.sendBodyAndHeaders(DIRECT_START_CACHE_TTL, SPAZZ_TESTING_SERVICE, headerMap);
        mock.assertIsSatisfied();

        // now change content in the file in the classpath and try again .... after delaying longer than the cache update delay
        Thread.sleep(1000);
        template.sendBodyAndHeader("file://target/test-classes/org/apache/camel/component/thymeleaf?fileExist=Override",
                "not-cached\n" + stringTemplate(), Exchange.FILE_NAME, "letter-cached.html");

        mock.reset();
        // we must expect the new content, because the cache has expired
        mock.expectedBodiesReceived(expectedNoCache());

        template.sendBodyAndHeaders(DIRECT_START_CACHE_TTL, SPAZZ_TESTING_SERVICE, headerMap);
        mock.assertIsSatisfied();
    }

    @Test
    public void testClearCacheViaJmx() throws Exception {

        MockEndpoint mock = getMockEndpoint(MOCK_RESULT);
        mock.expectedBodiesReceived(expected());

        Map<String, Object> headerMap = new HashMap<>(
                Map.ofEntries(entry(LAST_NAME, "Doe"), entry(FIRST_NAME, JANE), entry(ITEM, "Widgets for Dummies")));

        template.sendBodyAndHeaders(DIRECT_START_WITH_CACHE, SPAZZ_TESTING_SERVICE, headerMap);
        mock.assertIsSatisfied();

        // now change content in the file in the classpath and try again
        template.sendBodyAndHeader("file://target/test-classes/org/apache/camel/component/thymeleaf?fileExist=Override",
                "not-cached\n" + stringTemplate(), Exchange.FILE_NAME, "letter-cached.html");

        mock.reset();

        // we must expect the original file content since caching is enabled
        mock.expectedBodiesReceived(expected());

        template.sendBodyAndHeaders(DIRECT_START_WITH_CACHE, SPAZZ_TESTING_SERVICE, headerMap);
        mock.assertIsSatisfied();

        // clear the cache via the mbean server
        MBeanServer mbeanServer = context.getManagementStrategy().getManagementAgent().getMBeanServer();
        Set<ObjectName> objNameSet = mbeanServer.queryNames(
                new ObjectName("org.apache.camel:type=endpoints,name=\"thymeleaf:*cacheable=true*\",*"), null);
        // each endpoint has its own template resolver, so cycle through all of them
        for (ObjectName objectName : objNameSet) {
            mbeanServer.invoke(objectName, "clearContentCache", null, null);
        }

        // change content in the file in the classpath and try again
        template.sendBodyAndHeader(
                "file://target/test-classes/org/apache/camel/component/thymeleaf?fileExist=Override",
                "not-cached\n" + stringTemplate(), Exchange.FILE_NAME, "letter-cached.html");
        mock.reset();

        // we expect the updated file content because caching was disabled
        mock.expectedBodiesReceived(expectedNoCache());

        template.sendBodyAndHeaders(DIRECT_START_WITH_CACHE, SPAZZ_TESTING_SERVICE, headerMap);
        mock.assertIsSatisfied();

        // change content in the file in the classpath and try again to verify that the caching is still in effect after clearing the cache
        template.sendBodyAndHeader(
                "file://target/test-classes/org/apache/camel/component/thymeleaf?fileExist=Override",
                stringTemplate(), Exchange.FILE_NAME, "letter-cached.html");
        mock.reset();

        // we expect the cached content from the prior update
        mock.expectedBodiesReceived(expectedNoCache());

        template.sendBodyAndHeaders(DIRECT_START_WITH_CACHE, SPAZZ_TESTING_SERVICE, headerMap);
        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {

        return new RouteBuilder() {

            public void configure() {

                from(DIRECT_START_NO_CACHE)
                        .setProperty(ORDER_NUMBER, simple("7"))
                        .to("thymeleaf://org/apache/camel/component/thymeleaf/letter-cached.html?cacheable=false&allowContextMapAll=true")
                        .to(MOCK_RESULT);

                from(DIRECT_START_WITH_CACHE)
                        .setProperty(ORDER_NUMBER, simple("7"))
                        .to("thymeleaf://org/apache/camel/component/thymeleaf/letter-cached.html?cacheable=true&allowContextMapAll=true")
                        .to(MOCK_RESULT);

                from(DIRECT_START_CACHE_TTL)
                        .setProperty(ORDER_NUMBER, simple("7"))
                        .to("thymeleaf://org/apache/camel/component/thymeleaf/letter-cached.html?cacheable=true&cacheTimeToLive=100&allowContextMapAll=true")
                        .to(MOCK_RESULT);
            }
        };
    }

    private String expectedNoCache() {

        return "not-cached\n" + expected();
    }

}
