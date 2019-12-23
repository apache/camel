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
package org.apache.camel.component.facebook.data;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import facebook4j.Facebook;
import org.apache.camel.component.facebook.config.FacebookEndpointConfiguration;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Test {@link FacebookMethodsTypeHelper}.
 */
public class FacebookMethodsTypeHelperTest {

    private final List<String> searchIncludes;

    public FacebookMethodsTypeHelperTest() {
        searchIncludes = Arrays.asList("checkins", "events", "groups", "locations", "places", "posts", "users");
    }

    private String getShortName(String name) {
        if (name.startsWith("get")) {
            name = Character.toLowerCase(name.charAt(3)) + name.substring(4);
        } else if (name.startsWith("search") && !"search".equals(name)) {
            name = Character.toLowerCase(name.charAt(6)) + name.substring(7);
        }
        return name;
    }

    @Test
    public void testGetCandidateMethods() throws Exception {
        for (FacebookMethodsType method : FacebookMethodsType.values()) {
            final String name = method.getName();
            final String shortName = getShortName(method.getName());

            final String[] argNames = method.getArgNames().toArray(new String[method.getArgNames().size()]);
            List<FacebookMethodsType> candidates = FacebookMethodsTypeHelper.getCandidateMethods(name, argNames);
            assertFalse("No candidate methods for " + name, candidates.isEmpty());

            if (!name.equals(shortName) && !"search".equals(name)) {

                if (searchIncludes.contains(shortName)) {
                    candidates = FacebookMethodsTypeHelper.getCandidateMethods(
                        FacebookMethodsTypeHelper.convertToSearchMethod(shortName), new String[0]);
                    assertFalse("No candidate search methods for " + shortName, candidates.isEmpty());
                }
            }
        }
    }

    @Test
    public void testFilterMethods() throws Exception {
        // TODO
    }

    @Test
    public void testGetArguments() throws Exception {
        final Class<?>[] interfaces = Facebook.class.getInterfaces();
        for (Class<?> clazz : interfaces) {
            if (clazz.getName().endsWith("Methods")) {
                // check all methods of this Methods interface
                for (Method method : clazz.getDeclaredMethods()) {
                    // will throw an exception if can't be found
                    final List<Object> arguments = FacebookMethodsTypeHelper.getArguments(method.getName());
                    final int nArgs = arguments.size() / 2;
                    List<Class<?>> types = new ArrayList<>(nArgs);
                    for (int i = 0; i < nArgs; i++) {
                        types.add((Class<?>) arguments.get(2 * i));
                    }
                    assertTrue("Missing parameters for " + method,
                        types.containsAll(Arrays.asList(method.getParameterTypes())));
                }
            }
        }
    }

    @Test
    public void testAllArguments() throws Exception {
        assertFalse("Missing arguments", FacebookMethodsTypeHelper.allArguments().isEmpty());
    }

    @Test
    public void testGetType() throws Exception {
        for (Field field : FacebookEndpointConfiguration.class.getDeclaredFields()) {
            Class<?> expectedType = field.getType();
            // skip readingOptions
            if ("readingOptions".equals(field.getName())) {
                continue;
            }
            final Class<?> actualType = FacebookMethodsTypeHelper.getType(field.getName());
            // test for auto boxing, un-boxing
            if (actualType.isPrimitive()) {
                expectedType = (Class<?>) expectedType.getField("TYPE").get(null);
            } else if (List.class.isAssignableFrom(expectedType) && actualType.isArray()) {
                // skip lists, since they will be converted in invokeMethod()
                expectedType = actualType;
            }
            assertEquals("Missing property " + field.getName(), expectedType, actualType);
        }
    }

    @Test
    public void testConvertToGetMethod() throws Exception {
        assertEquals("Invalid get method name",
            FacebookMethodsType.GET_ACCOUNTS.getName(), FacebookMethodsTypeHelper.convertToGetMethod("accounts"));
    }

    @Test
    public void testConvertToSearchMethod() throws Exception {
        assertEquals("Invalid search method name",
            FacebookMethodsType.SEARCHPOSTS.getName(), FacebookMethodsTypeHelper.convertToSearchMethod("posts"));
    }

}
