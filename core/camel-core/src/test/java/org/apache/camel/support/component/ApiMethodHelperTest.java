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
package org.apache.camel.support.component;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.apache.camel.support.component.ApiMethodArg.arg;
import static org.junit.jupiter.api.Assertions.*;

public class ApiMethodHelperTest {

    private static TestMethod[] sayHis = new TestMethod[] { TestMethod.SAYHI, TestMethod.SAYHI_1 };
    private static ApiMethodHelper<TestMethod> apiMethodHelper;

    static {
        final HashMap<String, String> aliases = new HashMap<>();
        aliases.put("say(.*)", "$1");
        apiMethodHelper = new ApiMethodHelper<>(TestMethod.class, aliases, List.of("names"));
    }

    @Test
    public void testGetCandidateMethods() {
        List<ApiMethod> methods = apiMethodHelper.getCandidateMethods("sayHi");
        assertEquals(2, methods.size(), "Can't find sayHi(*)");

        methods = apiMethodHelper.getCandidateMethods("hi");
        assertEquals(2, methods.size(), "Can't find sayHi(name)");

        methods = apiMethodHelper.getCandidateMethods("hi", List.of("name"));
        assertEquals(1, methods.size(), "Can't find sayHi(name)");

        methods = apiMethodHelper.getCandidateMethods("greetMe");
        assertEquals(1, methods.size(), "Can't find greetMe(name)");

        methods = apiMethodHelper.getCandidateMethods("greetUs", List.of("name1"));
        assertEquals(1, methods.size(), "Can't find greetUs(name1, name2)");

        methods = apiMethodHelper.getCandidateMethods("greetAll", List.of("nameMap"));
        assertEquals(1, methods.size(), "Can't find greetAll(nameMap)");

        methods = apiMethodHelper.getCandidateMethods("greetInnerChild", List.of("child"));
        assertEquals(1, methods.size(), "Can't find greetInnerChild(child)");
    }

    @Test
    public void testFilterMethods() {
        List<ApiMethod> methods = apiMethodHelper.filterMethods(Arrays.asList(sayHis), ApiMethodHelper.MatchType.EXACT);
        assertEquals(1, methods.size(), "Exact match failed for sayHi()");
        assertEquals(TestMethod.SAYHI, methods.get(0), "Exact match failed for sayHi()");

        methods = apiMethodHelper.filterMethods(Arrays.asList(sayHis), ApiMethodHelper.MatchType.SUBSET);
        assertEquals(2, methods.size(), "Subset match failed for sayHi(*)");

        methods = apiMethodHelper.filterMethods(Arrays.asList(sayHis), ApiMethodHelper.MatchType.SUBSET, List.of("name"));
        assertEquals(1, methods.size(), "Subset match failed for sayHi(name)");
        assertEquals(TestMethod.SAYHI_1, methods.get(0), "Exact match failed for sayHi()");

        methods = apiMethodHelper.filterMethods(Arrays.asList(sayHis), ApiMethodHelper.MatchType.SUPER_SET,
                List.of("name"));
        assertEquals(1, methods.size(), "Super set match failed for sayHi(name)");
        assertEquals(TestMethod.SAYHI_1, methods.get(0), "Exact match failed for sayHi()");

        methods = apiMethodHelper.filterMethods(Arrays.asList(TestMethod.values()), ApiMethodHelper.MatchType.SUPER_SET,
                List.of("name"));
        assertEquals(2, methods.size(), "Super set match failed for sayHi(name)");

        // test nullable names
        methods = apiMethodHelper.filterMethods(
                Arrays.asList(TestMethod.GREETALL, TestMethod.GREETALL_1, TestMethod.GREETALL_2),
                ApiMethodHelper.MatchType.SUPER_SET);
        assertEquals(1, methods.size(), "Super set match with null args failed for greetAll(names)");
    }

    @Test
    public void testGetArguments() {
        assertEquals(2, apiMethodHelper.getArguments("hi").size(), "GetArguments failed for hi");
        assertEquals(2, apiMethodHelper.getArguments("greetMe").size(), "GetArguments failed for greetMe");
        assertEquals(4, apiMethodHelper.getArguments("greetUs").size(), "GetArguments failed for greetUs");
        assertEquals(6, apiMethodHelper.getArguments("greetAll").size(), "GetArguments failed for greetAll");
        assertEquals(2, apiMethodHelper.getArguments("greetInnerChild").size(), "GetArguments failed for greetInnerChild");
    }

    @Test
    public void testGetMissingProperties() throws Exception {
        assertEquals(1, apiMethodHelper.getMissingProperties("hi", new HashSet<String>()).size(), "Missing properties for hi");

        final HashSet<String> argNames = new HashSet<>();
        argNames.add("name");
        assertEquals(0, apiMethodHelper.getMissingProperties("greetMe", argNames).size(), "Missing properties for greetMe");

        argNames.clear();
        argNames.add("name1");
        assertEquals(1, apiMethodHelper.getMissingProperties("greetUs", argNames).size(), "Missing properties for greetMe");
    }

    @Test
    public void testAllArguments() throws Exception {
        assertEquals(8, apiMethodHelper.allArguments().size(), "Get all arguments");
    }

    @Test
    public void testGetType() throws Exception {
        assertEquals(String.class, apiMethodHelper.getType("name"), "Get type name");
        assertEquals(String.class, apiMethodHelper.getType("name1"), "Get type name1");
        assertEquals(String.class, apiMethodHelper.getType("name2"), "Get type name2");
        assertEquals(Map.class, apiMethodHelper.getType("nameMap"), "Get type nameMap");
        assertEquals(TestProxy.InnerChild.class, apiMethodHelper.getType("child"), "Get type child");
    }

    @Test
    public void testGetHighestPriorityMethod() throws Exception {
        assertEquals(TestMethod.SAYHI_1, ApiMethodHelper.getHighestPriorityMethod(Arrays.asList(sayHis)),
                "Get highest priority method");
    }

    @Test
    public void testInvokeMethod() throws Exception {
        TestProxy proxy = new TestProxy();
        assertEquals("Hello!", ApiMethodHelper.invokeMethod(proxy, TestMethod.SAYHI, Collections.emptyMap()),
                "sayHi()");

        final HashMap<String, Object> properties = new HashMap<>();
        properties.put("name", "Dave");

        assertEquals("Hello Dave", ApiMethodHelper.invokeMethod(proxy, TestMethod.SAYHI_1, properties), "sayHi(name)");
        assertEquals("Greetings Dave", ApiMethodHelper.invokeMethod(proxy, TestMethod.GREETME, properties), "greetMe(name)");

        properties.clear();
        properties.put("name1", "Dave");
        properties.put("name2", "Frank");
        assertEquals("Greetings Dave, Frank", ApiMethodHelper.invokeMethod(proxy, TestMethod.GREETUS, properties),
                "greetUs(name1, name2)");

        properties.clear();
        properties.put("names", new String[] { "Dave", "Frank" });
        assertEquals("Greetings Dave, Frank", ApiMethodHelper.invokeMethod(proxy, TestMethod.GREETALL, properties),
                "greetAll(names)");

        properties.clear();
        Map<String, String> nameMap = new HashMap<>();
        nameMap.put("Dave", "Hello");
        nameMap.put("Frank", "Goodbye");
        properties.put("nameMap", nameMap);
        final Map<String, String> result
                = (Map<String, String>) ApiMethodHelper.invokeMethod(proxy, TestMethod.GREETALL_2, properties);
        assertNotNull(result, "greetAll(nameMap)");
        for (Map.Entry<String, String> entry : result.entrySet()) {
            assertTrue(entry.getValue().endsWith(entry.getKey()), "greetAll(nameMap)");
        }

        // test with a derived proxy
        proxy = new TestProxy() {
            @Override
            public String sayHi(String name) {
                return "Howdy " + name;
            }
        };
        properties.clear();
        properties.put("name", "Dave");
        assertEquals("Howdy Dave", ApiMethodHelper.invokeMethod(proxy, TestMethod.SAYHI_1, properties), "Derived sayHi(name)");
    }

    enum TestMethod implements ApiMethod {

        SAYHI(String.class, "sayHi"),
        SAYHI_1(String.class, "sayHi", arg("name", String.class)),
        GREETME(String.class, "greetMe", arg("name", String.class)),
        GREETUS(String.class,
                "greetUs", arg("name1", String.class), arg("name2", String.class)),
        GREETALL(String.class, "greetAll", arg("names", String[].class)),
        GREETALL_1(String.class,
                   "greetAll", arg("nameList", List.class)),
        GREETALL_2(Map.class, "greetAll", arg("nameMap", Map.class)),
        GREETTIMES(String[].class, "greetTimes",
                   arg("name", String.class), arg("times", int.class)),
        GREETINNERCHILD(String[].class, "greetInnerChild",
                        arg("child", TestProxy.InnerChild.class));

        private final ApiMethod apiMethod;

        TestMethod(Class<?> resultType, String name, ApiMethodArg... args) {
            this.apiMethod = new ApiMethodImpl(TestProxy.class, resultType, name, args);
        }

        @Override
        public String getName() {
            return apiMethod.getName();
        }

        @Override
        public Class<?> getResultType() {
            return apiMethod.getResultType();
        }

        @Override
        public List<String> getArgNames() {
            return apiMethod.getArgNames();
        }

        @Override
        public List<Class<?>> getArgTypes() {
            return apiMethod.getArgTypes();
        }

        @Override
        public Method getMethod() {
            return apiMethod.getMethod();
        }
    }

}
