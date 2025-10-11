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
package org.apache.camel.service.lra;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LRASagaRoutesTest {

    private static final Logger LOG = LoggerFactory.getLogger(LRASagaRoutesTest.class);

    private Method getParseQueryMethod() throws NoSuchMethodException {
        Method method = LRASagaRoutes.class.getDeclaredMethod("parseQuery", String.class);
        method.setAccessible(true);
        return method;
    }

    @DisplayName("Tests whether parseQuery() is splitting unencoded query params correct.")
    @Test
    void testParseQuerySuccessUnencoded() throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {

        Map<String, String> testResult = (Map<String, String>) getParseQueryMethod()
                .invoke(new LRASagaRoutes(null),
                        "Camel-Saga-Compensate=direct://saga1_participant1_compensate&Camel-Saga-Complete=direct://saga1_participant1_complete");

        LOG.debug("Parsed query: {}", testResult);

        Assertions.assertNotNull(testResult, "pared query must not be null");
        Assertions.assertEquals(2, testResult.size(), "query parameter count must be two");
        Assertions.assertNotNull(testResult.get("Camel-Saga-Compensate"),
                "query parameter value for name 'Camel-Saga-Compensate' must not be null");
        Assertions.assertEquals("direct://saga1_participant1_compensate", testResult.get("Camel-Saga-Compensate"),
                "query parameter value for name 'Camel-Saga-Compensate' has unexpected content");
        Assertions.assertNotNull(testResult.get("Camel-Saga-Complete"),
                "query parameter value for name 'Camel-Saga-Complete' must not be null");
        Assertions.assertEquals("direct://saga1_participant1_complete", testResult.get("Camel-Saga-Complete"),
                "query parameter value for name 'Camel-Saga-Complete' has unexpected content");
    }

    @DisplayName("Tests parseQuery() to handle incorrect query string (no value is given)")
    @Test
    void testParseQueryInvalidUnencoded() throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {

        Map<String, String> testResult = (Map<String, String>) getParseQueryMethod()
                .invoke(new LRASagaRoutes(null), "key1=value1&key2");

        LOG.debug("Parsed query: {}", testResult);

        Assertions.assertNotNull(testResult, "pared query must not be null");
        Assertions.assertEquals(2, testResult.size(), "query parameter count must be two");
        Assertions.assertNotNull(testResult.get("key1"),
                "query parameter value for name 'key1' must not be null");
        Assertions.assertEquals("value1", testResult.get("key1"),
                "query parameter value for name 'key1' has unexpected content");
        Assertions.assertNotNull(testResult.get("key2"),
                "query parameter value for name 'key2' must not be null");
        Assertions.assertEquals("", testResult.get("key2"),
                "query parameter value for name 'key2' has unexpected content");
    }

    @DisplayName("Tests parseQuery() to handle incorrect query string (no value is given)")
    @Test
    void testParseQuerySuccessEncoded() throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {

        Map<String, String> testResult = (Map<String, String>) getParseQueryMethod()
                .invoke(new LRASagaRoutes(null),
                        "Camel-Saga-Compensate=direct%3A%2F%2Fsaga1_participant1_compensate&Camel-Saga-Complete=direct%3A%2F%2Fsaga1_participant1_complete");

        LOG.debug("Parsed query: {}", testResult);

        Assertions.assertNotNull(testResult, "pared query must not be null");
        Assertions.assertEquals(2, testResult.size(), "query parameter count must be two");
        Assertions.assertNotNull(testResult.get("Camel-Saga-Compensate"),
                "query parameter value for name 'Camel-Saga-Compensate' must not be null");
        Assertions.assertEquals("direct://saga1_participant1_compensate", testResult.get("Camel-Saga-Compensate"),
                "query parameter value for name 'Camel-Saga-Compensate' has unexpected content");
        Assertions.assertNotNull(testResult.get("Camel-Saga-Complete"),
                "query parameter value for name 'Camel-Saga-Complete' must not be null");
        Assertions.assertEquals("direct://saga1_participant1_complete", testResult.get("Camel-Saga-Complete"),
                "query parameter value for name 'Camel-Saga-Complete' has unexpected content");
    }
}
