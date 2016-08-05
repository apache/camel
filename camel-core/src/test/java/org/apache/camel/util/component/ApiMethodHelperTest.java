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
package org.apache.camel.util.component;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import static org.apache.camel.util.component.ApiMethodArg.arg;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ApiMethodHelperTest {

    private static TestMethod[] sayHis = new TestMethod[] {TestMethod.SAYHI, TestMethod.SAYHI_1};
    private static ApiMethodHelper<TestMethod> apiMethodHelper;

    static {
        final HashMap<String, String> aliases = new HashMap<String, String>();
        aliases.put("say(.*)", "$1");
        apiMethodHelper = new ApiMethodHelper<TestMethod>(TestMethod.class, aliases, Arrays.asList("names"));
    }

    @Test
    public void testGetCandidateMethods() {
        List<ApiMethod> methods = apiMethodHelper.getCandidateMethods("sayHi");
        assertEquals("Can't find sayHi(*)", 2, methods.size());

        methods = apiMethodHelper.getCandidateMethods("hi");
        assertEquals("Can't find sayHi(name)", 2, methods.size());

        methods = apiMethodHelper.getCandidateMethods("hi", Arrays.asList("name"));
        assertEquals("Can't find sayHi(name)", 1, methods.size());

        methods = apiMethodHelper.getCandidateMethods("greetMe");
        assertEquals("Can't find greetMe(name)", 1, methods.size());

        methods = apiMethodHelper.getCandidateMethods("greetUs", Arrays.asList("name1"));
        assertEquals("Can't find greetUs(name1, name2)", 1, methods.size());

        methods = apiMethodHelper.getCandidateMethods("greetAll", Arrays.asList("nameMap"));
        assertEquals("Can't find greetAll(nameMap)", 1, methods.size());

        methods = apiMethodHelper.getCandidateMethods("greetInnerChild", Arrays.asList("child"));
        assertEquals("Can't find greetInnerChild(child)", 1, methods.size());
    }

    @Test
    public void testFilterMethods() {
        List<ApiMethod> methods = apiMethodHelper.filterMethods(Arrays.asList(sayHis), ApiMethodHelper.MatchType.EXACT);
        assertEquals("Exact match failed for sayHi()", 1, methods.size());
        assertEquals("Exact match failed for sayHi()", TestMethod.SAYHI, methods.get(0));

        methods = apiMethodHelper.filterMethods(Arrays.asList(sayHis), ApiMethodHelper.MatchType.SUBSET);
        assertEquals("Subset match failed for sayHi(*)", 2, methods.size());

        methods = apiMethodHelper.filterMethods(Arrays.asList(sayHis), ApiMethodHelper.MatchType.SUBSET, Arrays.asList("name"));
        assertEquals("Subset match failed for sayHi(name)", 1, methods.size());
        assertEquals("Exact match failed for sayHi()", TestMethod.SAYHI_1, methods.get(0));

        methods = apiMethodHelper.filterMethods(Arrays.asList(sayHis), ApiMethodHelper.MatchType.SUPER_SET, Arrays.asList("name"));
        assertEquals("Super set match failed for sayHi(name)", 1, methods.size());
        assertEquals("Exact match failed for sayHi()", TestMethod.SAYHI_1, methods.get(0));

        methods = apiMethodHelper.filterMethods(Arrays.asList(TestMethod.values()), ApiMethodHelper.MatchType.SUPER_SET, Arrays.asList("name"));
        assertEquals("Super set match failed for sayHi(name)", 2, methods.size());

        // test nullable names
        methods = apiMethodHelper.filterMethods(
            Arrays.asList(TestMethod.GREETALL, TestMethod.GREETALL_1, TestMethod.GREETALL_2),
            ApiMethodHelper.MatchType.SUPER_SET);
        assertEquals("Super set match with null args failed for greetAll(names)", 1, methods.size());
    }

    @Test
    public void testGetArguments() {
        assertEquals("GetArguments failed for hi", 2, apiMethodHelper.getArguments("hi").size());
        assertEquals("GetArguments failed for greetMe", 2, apiMethodHelper.getArguments("greetMe").size());
        assertEquals("GetArguments failed for greetUs", 4, apiMethodHelper.getArguments("greetUs").size());
        assertEquals("GetArguments failed for greetAll", 6, apiMethodHelper.getArguments("greetAll").size());
        assertEquals("GetArguments failed for greetInnerChild", 2, apiMethodHelper.getArguments("greetInnerChild").size());
    }

    @Test
    public void testGetMissingProperties() throws Exception {
        assertEquals("Missing properties for hi", 1,
                apiMethodHelper.getMissingProperties("hi", new HashSet<String>()).size());

        final HashSet<String> argNames = new HashSet<String>();
        argNames.add("name");
        assertEquals("Missing properties for greetMe", 0,
                apiMethodHelper.getMissingProperties("greetMe", argNames).size());

        argNames.clear();
        argNames.add("name1");
        assertEquals("Missing properties for greetMe", 1,
                apiMethodHelper.getMissingProperties("greetUs", argNames).size());
    }

    @Test
    public void testAllArguments() throws Exception {
        assertEquals("Get all arguments", 8, apiMethodHelper.allArguments().size());
    }

    @Test
    public void testGetType() throws Exception {
        assertEquals("Get type name", String.class, apiMethodHelper.getType("name"));
        assertEquals("Get type name1", String.class, apiMethodHelper.getType("name1"));
        assertEquals("Get type name2", String.class, apiMethodHelper.getType("name2"));
        assertEquals("Get type nameMap", Map.class, apiMethodHelper.getType("nameMap"));
        assertEquals("Get type child", TestProxy.InnerChild.class, apiMethodHelper.getType("child"));
    }

    @Test
    public void testGetHighestPriorityMethod() throws Exception {
        assertEquals("Get highest priority method",
                TestMethod.SAYHI_1, ApiMethodHelper.getHighestPriorityMethod(Arrays.asList(sayHis)));
    }

    @Test
    public void testInvokeMethod() throws Exception {
        TestProxy proxy = new TestProxy();
        assertEquals("sayHi()", "Hello!", ApiMethodHelper.invokeMethod(proxy, TestMethod.SAYHI, Collections.<String, Object>emptyMap()));

        final HashMap<String, Object> properties = new HashMap<String, Object>();
        properties.put("name", "Dave");

        assertEquals("sayHi(name)", "Hello Dave", ApiMethodHelper.invokeMethod(proxy, TestMethod.SAYHI_1, properties));
        assertEquals("greetMe(name)", "Greetings Dave", ApiMethodHelper.invokeMethod(proxy, TestMethod.GREETME, properties));

        properties.clear();
        properties.put("name1", "Dave");
        properties.put("name2", "Frank");
        assertEquals("greetUs(name1, name2)", "Greetings Dave, Frank", ApiMethodHelper.invokeMethod(proxy, TestMethod.GREETUS, properties));

        properties.clear();
        properties.put("names", new String[] {"Dave", "Frank"});
        assertEquals("greetAll(names)", "Greetings Dave, Frank", ApiMethodHelper.invokeMethod(proxy, TestMethod.GREETALL, properties));

        properties.clear();
        Map<String, String> nameMap = new HashMap<String, String>();
        nameMap.put("Dave", "Hello");
        nameMap.put("Frank", "Goodbye");
        properties.put("nameMap", nameMap);
        final Map<String, String> result = (Map<String, String>) ApiMethodHelper.invokeMethod(proxy, TestMethod.GREETALL_2, properties);
        assertNotNull("greetAll(nameMap)", result);
        for (Map.Entry<String, String> entry : result.entrySet()) {
            assertTrue("greetAll(nameMap)", entry.getValue().endsWith(entry.getKey()));
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
        assertEquals("Derived sayHi(name)", "Howdy Dave", ApiMethodHelper.invokeMethod(proxy, TestMethod.SAYHI_1, properties));
    }

    enum TestMethod implements ApiMethod {

        SAYHI(String.class, "sayHi"),
        SAYHI_1(String.class, "sayHi", arg("name", String.class)),
        GREETME(String.class, "greetMe", arg("name", String.class)),
        GREETUS(String.class, "greetUs", arg("name1", String.class), arg("name2", String.class)),
        GREETALL(String.class, "greetAll", arg("names", new String[0].getClass())),
        GREETALL_1(String.class, "greetAll", arg("nameList", List.class)),
        GREETALL_2(Map.class, "greetAll", arg("nameMap", Map.class)),
        GREETTIMES(new String[0].getClass(), "greetTimes", arg("name", String.class), arg("times", int.class)),
        GREETINNERCHILD(new String[0].getClass(), "greetInnerChild", arg("child", TestProxy.InnerChild.class));

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
