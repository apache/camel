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
package org.apache.camel.test.spring;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.camel.spring.SpringCamelContext;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestContext;

/**
 * Helper that provides state information across the levels of Spring Test that do not expose the
 * necessary context/state for integration of Camel testing features into Spring test.  Also
 * provides utility methods.
 * <p/>
 * Note that this class makes use of {@link ThreadLocal}s to maintain some state.  It is imperative
 * that the state setters and getters are accessed within the scope of a single thread in order
 * for this class to work right.
 */
public final class CamelSpringTestHelper {
    
    private static ThreadLocal<String> originalJmxDisabledValue = new ThreadLocal<>();
    private static ThreadLocal<Class<?>> testClazz = new ThreadLocal<>();
    private static ThreadLocal<TestContext> testContext = new ThreadLocal<>();

    private CamelSpringTestHelper() {
    }
    
    public static String getOriginalJmxDisabled() {
        return originalJmxDisabledValue.get();
    }
    
    public static void setOriginalJmxDisabledValue(String originalValue) {
        originalJmxDisabledValue.set(originalValue);
    }
    
    public static Class<?> getTestClass() {
        return testClazz.get();
    }
    
    public static void setTestClass(Class<?> testClass) {
        testClazz.set(testClass);
    }

    public static Method getTestMethod() {
        return testContext.get().getTestMethod();
    }

    public static void setTestContext(TestContext context) {
        testContext.set(context);
    }

    /**
     * Returns all methods defined in {@code clazz} and its superclasses/interfaces.
     */
    public static Collection<Method> getAllMethods(Class<?> clazz)  {
        Set<Method> methods = new LinkedHashSet<>();
        Class<?> currentClass = clazz;
        
        while (currentClass != null) {
            methods.addAll(Arrays.asList(clazz.getMethods()));
            currentClass = currentClass.getSuperclass(); 
        }
                
        return methods;
    }
    
    /**
     * Executes {@code strategy} against all {@link SpringCamelContext}s found in the Spring context.
     * This method reduces the amount of repeated find and loop code throughout this class.
     *
     * @param context the Spring context to search
     * @param strategy the strategy to execute against the found {@link SpringCamelContext}s
     *
     * @throws Exception if there is an error executing any of the strategies
     */
    public static void doToSpringCamelContexts(ApplicationContext context, DoToSpringCamelContextsStrategy strategy) throws Exception {
        Map<String, SpringCamelContext> contexts = context.getBeansOfType(SpringCamelContext.class);
        
        for (Entry<String, SpringCamelContext> entry : contexts.entrySet()) {
            strategy.execute(entry.getKey(), entry.getValue());
        }
    }
    
    public interface DoToSpringCamelContextsStrategy {
        void execute(String contextName, SpringCamelContext camelContext) throws Exception;
    }
}
