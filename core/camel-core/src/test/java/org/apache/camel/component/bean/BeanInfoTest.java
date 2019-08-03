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
package org.apache.camel.component.bean;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.ExchangePattern;
import org.apache.camel.InOnly;
import org.apache.camel.InOut;
import org.apache.camel.Pattern;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BeanInfoTest extends Assert {
    private static final Logger LOG = LoggerFactory.getLogger(BeanInfoTest.class);

    protected CamelContext camelContext = new DefaultCamelContext();

    @Test
    public void testObjectOperations() throws Exception {
        BeanInfo info = createBeanInfo(Object.class);

        List<MethodInfo> operations = info.getMethods();
        assertEquals(1, operations.size());
        assertEquals("toString", operations.get(0).getMethod().getName());
    }

    @Test
    public void testGetOperations() throws Exception {
        BeanInfo info = createBeanInfo(Foo.class);

        List<MethodInfo> operations = info.getMethods();
        assertEquals(2, operations.size());

        long size = operations.stream().filter(m -> m.getMethod().getName().equals("inOnlyMethod")).count();
        assertEquals(1, size);

        size = operations.stream().filter(m -> m.getMethod().getName().equals("inOutMethod")).count();
        assertEquals(1, size);
    }

    @Test
    public void testMethodPatternUsingMethodAnnotations() throws Exception {
        BeanInfo info = createBeanInfo(Foo.class);

        assertMethodPattern(info, "inOutMethod", ExchangePattern.InOut);
        assertMethodPattern(info, "inOnlyMethod", ExchangePattern.InOnly);
    }

    @Test
    public void testMethodPatternUsingClassAnnotationsOnInterface() throws Exception {
        BeanInfo info = createBeanInfo(MyOneWayInterface.class);

        assertMethodPattern(info, "inOnlyMethod", ExchangePattern.InOnly);
    }

    @Test
    public void testMethodPatternUsingMethodAnnotationsOnInterface() throws Exception {
        BeanInfo info = createBeanInfo(MyOneWayInterfaceWithOverloadedMethod.class);

        assertMethodPattern(info, "inOnlyMethod", ExchangePattern.InOnly);
        assertMethodPattern(info, "inOutMethod", ExchangePattern.InOut);
    }

    @Test
    public void testMethodPatternUsingClassAnnotationsButOverloadingOnMethod() throws Exception {
        BeanInfo info = createBeanInfo(OverloadOnMethod.class);

        assertMethodPattern(info, "inOnlyMethod", ExchangePattern.InOnly);
    }

    @Test
    public void testMethodPatternUsingClassAnnotationsButOverloadingOnBaseClassMethod() throws Exception {
        BeanInfo info = createBeanInfo(OverloadOnBaseClass.class);

        assertMethodPattern(info, "inOnlyMethod", ExchangePattern.InOnly);
    }

    @Test
    public void testMethodPatternUsingClassAnnotationsOnClassWithAnnotationsOnInterface() throws Exception {
        BeanInfo info = createBeanInfo(OverloadOnMethod.class);

        assertMethodPattern(info, "inOnlyMethod", ExchangePattern.InOnly);
    }

    @Test
    public void testMethodPatternUsingClassAnnotationsOnBaseInterfaceAndOverloadingMethodOnDerivedInterface() throws Exception {
        BeanInfo info = createBeanInfo(OverloadOnInterface.class);

        assertMethodPattern(info, "inOnlyMethod", ExchangePattern.InOnly);
        assertMethodPattern(info, "inOutMethod", ExchangePattern.InOut);
    }

    @Test
    public void testImplementLevel2InterfaceMethodInPackagePrivateClass() {
        BeanInfo info = createBeanInfo(PackagePrivateClassImplementingLevel2InterfaceMethod.class);
        List<MethodInfo> mis = info.getMethods();
        Assert.assertNotNull(mis);
        Assert.assertEquals(1, mis.size());
        MethodInfo mi = mis.get(0);
        Assert.assertNotNull(mi);
        Method m = mi.getMethod();
        Assert.assertEquals("method", m.getName());
        Assert.assertTrue(Modifier.isPublic(m.getDeclaringClass().getModifiers()));
    }

    @Test
    public void testPublicClassImplementingInterfaceMethodBySuperPackagePrivateClass() {
        BeanInfo info = createBeanInfo(PublicClassImplementingBySuperPackagePrivateClass.class);
        List<MethodInfo> mis = info.getMethods();
        Assert.assertNotNull(mis);
        Assert.assertEquals(1, mis.size());
        MethodInfo mi = mis.get(0);
        Assert.assertNotNull(mi);
        Method m = mi.getMethod();
        Assert.assertEquals("method", m.getName());
        Assert.assertTrue(Modifier.isPublic(m.getDeclaringClass().getModifiers()));
    }

    protected BeanInfo createBeanInfo(Class<?> type) {
        BeanInfo info = new BeanInfo(camelContext, type);
        return info;
    }

    protected void assertMethodPattern(BeanInfo info, String methodName, ExchangePattern expectedPattern) throws NoSuchMethodException {
        Class<?> type = info.getType();
        Method method = type.getMethod(methodName);
        assertNotNull("Could not find method: " + methodName, method);

        MethodInfo methodInfo = info.getMethodInfo(method);
        assertNotNull("Could not find methodInfo for: " + method, methodInfo);

        ExchangePattern actualPattern = methodInfo.getPattern();
        assertEquals("Pattern for: " + method, expectedPattern, actualPattern);

        LOG.info("Method: {} has pattern: {}", method, actualPattern);
    }

    public interface Foo {
        void inOutMethod();

        @Pattern(ExchangePattern.InOnly)
        void inOnlyMethod();
    }

    @InOnly
    public interface MyOneWayInterface {
        void inOnlyMethod();
    }

    @InOnly
    public interface MyOneWayInterfaceWithOverloadedMethod {
        void inOnlyMethod();

        @InOut
        Object inOutMethod();
    }

    public static class OverloadOnMethod implements MyOneWayInterface {

        @Override
        public void inOnlyMethod() {
        }
    }

    public static class OverloadOnBaseClass extends OverloadOnMethod {
        public void robustInOnlyMethod() {
        }
    }

    public static class OverloadOnInterface implements MyOneWayInterfaceWithOverloadedMethod {

        @Override
        public void inOnlyMethod() {
        }

        @Override
        public Object inOutMethod() {
            return null;
        }
    }

    public interface ILevel2Interface {
        String method();
    }

    public interface ILevel1Interface extends ILevel2Interface {
    }

    class PackagePrivateClassImplementingLevel2InterfaceMethod implements ILevel1Interface {
        @Override
        public String method() {
            return "PackagePrivateClassImplementingLevel2InterfaceMethod.method() has been called";
        }
    }

    public interface IMethodInterface {
        String method();
    }

    class PackagePrivateClassDefiningMethod {
        public String method() {
            return "PackagePrivateClassDefiningMethod.method() has been called";
        }
    }

    public class PublicClassImplementingBySuperPackagePrivateClass extends PackagePrivateClassDefiningMethod implements IMethodInterface {
    }

}
