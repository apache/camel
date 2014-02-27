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
package org.apache.camel.component.facebook.data;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import facebook4j.Facebook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Test that all *Methods methods are mapped in {@link FacebookMethodsType}.
 */
public class FacebookMethodsTypeTest {

    private static final Logger LOG = LoggerFactory.getLogger(FacebookMethodsTypeTest.class);
    private static final String LINE_SEPARATOR = System.getProperty("line.separator");

    @Test
    public void areAllMethodsMapped() throws Exception {
        final Class<?>[] interfaces = Facebook.class.getInterfaces();
        for (Class<?> clazz : interfaces) {
            if (clazz.getName().endsWith("Methods")) {
                // check all methods of this *Methods interface
                for (Method method : clazz.getDeclaredMethods()) {
                    final FacebookMethodsType methodsType = FacebookMethodsType.findMethod(method.getName(), method.getParameterTypes());
                    assertNotNull(methodsType);
                    assertEquals("Methods are not equal", method, methodsType.getMethod());
                }
            }
        }
    }

    @Test
    public void printMethodInfo() {
        // map method names to number of overloads
        Map<String, Integer> methodCountMap = new LinkedHashMap<String, Integer>();

        // map method names to options, along with a count of overloads that use that option
        Map<String, Map<String, Integer>> optionsMap = new LinkedHashMap<String, Map<String, Integer>>();

        for (FacebookMethodsType method : FacebookMethodsType.values()) {

            final String name = method.getName();
            Integer methodCount = methodCountMap.get(name);
            if (methodCount == null) {
                methodCount = 1;
            } else {
                methodCount = ++methodCount;
            }
            methodCountMap.put(name, methodCount);

            Map<String, Integer> options = optionsMap.get(name);
            if (options == null) {
                options = new LinkedHashMap<String, Integer>();
                optionsMap.put(name, options);
            }
            for (String option : method.getArgNames()) {
                Integer optionCount = options.get(option);
                if (optionCount == null) {
                    optionCount = 1;
                } else {
                    optionCount = ++optionCount;
                }
                options.put(option, optionCount);
            }
        }

        // print method names and options
        final StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, Integer> methodCount : methodCountMap.entrySet()) {
            final String name = methodCount.getKey();
            final int mCount = methodCount.getValue();

            builder.setLength(0);
            builder.append(name)
                .append(',')
                .append(getShortName(name));
            for (Map.Entry<String, Integer> option : optionsMap.get(name).entrySet()) {
                builder.append(',');
                if (option.getValue() < mCount) {
                    builder.append('[')
                        .append(option.getKey())
                        .append(']');
                } else {
                    builder.append(option.getKey());
                }
            }
            LOG.info(builder.toString());
        }
    }

    private String getShortName(String name) {
        if (name.startsWith("get")) {
            name = Character.toLowerCase(name.charAt(3)) + name.substring(4);
        } else if (name.startsWith("search") && !"search".equals(name)) {
            name = Character.toLowerCase(name.charAt(6)) + name.substring(7);
        }
        return name;
    }

}
