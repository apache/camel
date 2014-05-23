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

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class ApiMethodHelperTest {

    private static TestMethod[] sayHis = new TestMethod[] { TestMethod.SAYHI, TestMethod.SAYHI_1};
    private static ApiMethodHelper<TestMethod> apiMethodHelper;

    static {
        final HashMap<String, String> aliases = new HashMap<String, String>();
        aliases.put("say(.*)", "$1");
        apiMethodHelper = new ApiMethodHelper<TestMethod>(TestMethod.class, aliases);
    }

    @Test
    public void testGetCandidateMethods() {
        List<TestMethod> methods = apiMethodHelper.getCandidateMethods("sayHi");
        assertEquals("Can't find sayHi(*)", 2, methods.size());

        methods = apiMethodHelper.getCandidateMethods("hi");
        assertEquals("Can't find sayHi(name)", 2, methods.size());

        methods = apiMethodHelper.getCandidateMethods("hi", "name");
        assertEquals("Can't find sayHi(name)", 1, methods.size());

        methods = apiMethodHelper.getCandidateMethods("greetMe");
        assertEquals("Can't find greetMe(name)", 1, methods.size());

        methods = apiMethodHelper.getCandidateMethods("greetUs", "name1");
        assertEquals("Can't find greetUs(name1, name2)", 1, methods.size());
    }

    @Test
    public void testFilterMethods() {
        List<TestMethod> methods = apiMethodHelper.filterMethods(Arrays.asList(sayHis), ApiMethodHelper.MatchType.EXACT);
        assertEquals("Exact match failed for sayHi()", 1, methods.size());
        assertEquals("Exact match failed for sayHi()", TestMethod.SAYHI, methods.get(0));

        methods = apiMethodHelper.filterMethods(Arrays.asList(sayHis), ApiMethodHelper.MatchType.SUBSET);
        assertEquals("Subset match failed for sayHi(*)", 2, methods.size());

        methods = apiMethodHelper.filterMethods(Arrays.asList(sayHis), ApiMethodHelper.MatchType.SUBSET, "name");
        assertEquals("Subset match failed for sayHi(name)", 1, methods.size());
        assertEquals("Exact match failed for sayHi()", TestMethod.SAYHI_1, methods.get(0));

        methods = apiMethodHelper.filterMethods(Arrays.asList(sayHis), ApiMethodHelper.MatchType.SUPER_SET, "name");
        assertEquals("Super set match failed for sayHi(name)", 1, methods.size());
        assertEquals("Exact match failed for sayHi()", TestMethod.SAYHI_1, methods.get(0));

        methods = apiMethodHelper.filterMethods(Arrays.asList(TestMethod.values()), ApiMethodHelper.MatchType.SUPER_SET, "name");
        assertEquals("Super set match failed for sayHi(name)", 2, methods.size());
    }

    @Test
    public void testGetArguments() {
        assertEquals("GetArguments failed for hi", 2, apiMethodHelper.getArguments("hi").size());
        assertEquals("GetArguments failed for greetMe", 2, apiMethodHelper.getArguments("greetMe").size());
        assertEquals("GetArguments failed for greetUs", 4, apiMethodHelper.getArguments("greetUs").size());
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
        assertEquals("Get all arguments", 6, apiMethodHelper.allArguments().size());
    }

    @Test
    public void testGetType() throws Exception {
        assertEquals("Get type name", String.class, apiMethodHelper.getType("name"));
        assertEquals("Get type name1", String.class, apiMethodHelper.getType("name1"));
        assertEquals("Get type name2", String.class, apiMethodHelper.getType("name2"));
    }

    @Test
    public void testGetHighestPriorityMethod() throws Exception {
        assertEquals("Get highest priority method",
                TestMethod.SAYHI_1, apiMethodHelper.getHighestPriorityMethod(Arrays.asList(sayHis)));
    }

    @Test
    public void testInvokeMethod() throws Exception {
        TestProxy proxy = new TestProxy();
        assertEquals("sayHi()", "Hello!", apiMethodHelper.invokeMethod(proxy, TestMethod.SAYHI, Collections.EMPTY_MAP));

        final HashMap<String, Object> properties = new HashMap<String, Object>();
        properties.put("name", "Dave");

        assertEquals("sayHi(name)", "Hello Dave", apiMethodHelper.invokeMethod(proxy, TestMethod.SAYHI_1, properties));
        assertEquals("greetMe(name)", "Greetings Dave", apiMethodHelper.invokeMethod(proxy, TestMethod.GREETME, properties));

        properties.clear();
        properties.put("name1", "Dave");
        properties.put("name2", "Frank");
        assertEquals("greetUs(name1, name2)", "Greetings Dave, Frank", apiMethodHelper.invokeMethod(proxy, TestMethod.GREETUS, properties));
    }

    static enum TestMethod implements ApiMethod {

        SAYHI(String.class, "sayHi"),
        SAYHI_1(String.class, "sayHi", String.class, "name"),
        GREETME(String.class, "greetMe", String.class, "name"),
        GREETUS(String.class, "greetUs", String.class, "name1", String.class, "name2"),
        GREETALL(String.class, "greetAll", new String[0].getClass(), "names"),
        GREETALL_1(String.class, "greetAll", List.class, "nameList"),
        GREETTIMES(new String[0].getClass(), "greetTimes", String.class, "name", int.class, "times");

        private final ApiMethod apiMethod;

        private TestMethod(Class<?> resultType, String name, Object... args) {
            this.apiMethod = new ApiMethodImpl(TestProxy.class, resultType, name, args);
        }

        @Override
        public String getName() { return apiMethod.getName(); }

        @Override
        public Class<?> getResultType() { return apiMethod.getResultType(); }

        @Override
        public List<String> getArgNames() { return apiMethod.getArgNames(); }

        @Override
        public List<Class<?>> getArgTypes() { return apiMethod.getArgTypes(); }

        @Override
        public Method getMethod() { return apiMethod.getMethod(); }
    }

}
