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
package org.apache.camel.component.bean;

import java.lang.reflect.Method;

import junit.framework.TestCase;

import org.apache.camel.CamelContext;
import org.apache.camel.ExchangePattern;
import org.apache.camel.InOnly;
import org.apache.camel.InOut;
import org.apache.camel.Pattern;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * @version $Revision$
 */
public class BeanInfoTest extends TestCase {
    private static final transient Log LOG = LogFactory.getLog(BeanInfoTest.class);

    protected CamelContext camelContext = new DefaultCamelContext();

    public void testMethodPatternUsingMethodAnnotations() throws Exception {
        BeanInfo info = createBeanInfo(Foo.class);

        assertMethodPattern(info, "inOutMethod", ExchangePattern.InOut);
        assertMethodPattern(info, "inOnlyMethod", ExchangePattern.InOnly);
        assertMethodPattern(info, "robustInOnlyMethod", ExchangePattern.RobustInOnly);
    }

    public void testMethodPatternUsingClassAnnotationsOnInterface() throws Exception {
        BeanInfo info = createBeanInfo(MyOneWayInterface.class);

        assertMethodPattern(info, "inOnlyMethod", ExchangePattern.InOnly);
    }

    public void testMethodPatternUsingMethodAnnotationsOnInterface() throws Exception {
        BeanInfo info = createBeanInfo(MyOneWayInterfaceWithOverloadedMethod.class);

        assertMethodPattern(info, "inOnlyMethod", ExchangePattern.InOnly);
        assertMethodPattern(info, "robustInOnlyMethod", ExchangePattern.RobustInOnly);
        assertMethodPattern(info, "inOutMethod", ExchangePattern.InOut);
    }

    public void testMethodPatternUsingClassAnnotationsButOverloadingOnMethod() throws Exception {
        BeanInfo info = createBeanInfo(OverloadOnMethod.class);

        assertMethodPattern(info, "inOnlyMethod", ExchangePattern.InOnly);
        assertMethodPattern(info, "robustInOnlyMethod", ExchangePattern.RobustInOnly);
    }

    public void testMethodPatternUsingClassAnnotationsButOverloadingOnBaseClassMethod() throws Exception {
        BeanInfo info = createBeanInfo(OverloadOnBaseClass.class);

        assertMethodPattern(info, "inOnlyMethod", ExchangePattern.InOnly);
        assertMethodPattern(info, "robustInOnlyMethod", ExchangePattern.RobustInOnly);
    }

    public void testMethodPatternUsingClassAnnotationsOnClassWithAnnotationsOnInterface() throws Exception {
        BeanInfo info = createBeanInfo(OverloadOnMethod.class);

        assertMethodPattern(info, "inOnlyMethod", ExchangePattern.InOnly);
        assertMethodPattern(info, "robustInOnlyMethod", ExchangePattern.RobustInOnly);
    }

    public void testMethodPatternUsingClassAnnotationsOnBaseInterfaceAndOverloadingMethodOnDerivedInterface() throws Exception {
        BeanInfo info = createBeanInfo(OverloadOnInterface.class);

        assertMethodPattern(info, "inOnlyMethod", ExchangePattern.InOnly);
        assertMethodPattern(info, "robustInOnlyMethod", ExchangePattern.RobustInOnly);
        assertMethodPattern(info, "inOutMethod", ExchangePattern.InOut);
    }

    protected BeanInfo createBeanInfo(Class type) {
        BeanInfo info = new BeanInfo(camelContext, type);
        return info;
    }

    protected void assertMethodPattern(BeanInfo info, String methodName, ExchangePattern expectedPattern) throws NoSuchMethodException {
        Class type = info.getType();
        Method method = type.getMethod(methodName);
        assertNotNull("Could not find method: " + methodName, method);

        MethodInfo methodInfo = info.getMethodInfo(method);
        assertNotNull("Could not find methodInfo for: " + method, methodInfo);

        ExchangePattern actualPattern = methodInfo.getPattern();
        assertEquals("Pattern for: " + method, expectedPattern, actualPattern);

        LOG.info("Method: " + method + " has pattern: " + actualPattern);
    }

    public interface Foo {
        void inOutMethod();

        @Pattern(ExchangePattern.InOnly)
        void inOnlyMethod();

        @Pattern(ExchangePattern.RobustInOnly)
        void robustInOnlyMethod();
    }

    @InOnly
    public interface MyOneWayInterface {
        void inOnlyMethod();
    }

    @InOnly
    public interface MyOneWayInterfaceWithOverloadedMethod {
        void inOnlyMethod();

        @Pattern(ExchangePattern.RobustInOnly)
        void robustInOnlyMethod();

        @InOut
        Object inOutMethod();
    }

    public static class OverloadOnMethod implements MyOneWayInterface {

        public void inOnlyMethod() {
        }

        @Pattern(ExchangePattern.RobustInOnly)
        public void robustInOnlyMethod() {
        }
    }

    public static class OverloadOnBaseClass extends OverloadOnMethod {
        public void robustInOnlyMethod() {
        }
    }

    public static class OverloadOnInterface implements MyOneWayInterfaceWithOverloadedMethod {

        public void inOnlyMethod() {
        }

        public void robustInOnlyMethod() {
        }

        public Object inOutMethod() {
            return null;
        }
    }

}
