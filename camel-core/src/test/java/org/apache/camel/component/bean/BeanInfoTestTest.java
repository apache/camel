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
import org.apache.camel.OneWay;
import org.apache.camel.impl.DefaultCamelContext;

/**
 * @version $Revision: 1.1 $
 */
public class BeanInfoTestTest extends TestCase {
    protected CamelContext camelContext = new DefaultCamelContext();
    protected BeanInfo info = new BeanInfo(camelContext, Foo.class);

    public void testMethodPattern() throws Exception {
        assertMethodPattern("inOutMethod", ExchangePattern.InOut);
        assertMethodPattern("inOnlyMethod", ExchangePattern.InOnly);
        assertMethodPattern("robustInOnlyMethod", ExchangePattern.RobustInOnly);
    }

    protected void assertMethodPattern(String methodName, ExchangePattern expectedPattern) throws NoSuchMethodException {
        Class type = info.getType();
        Method method = type.getMethod(methodName);
        assertNotNull("Could not find method: " + methodName, method);

        MethodInfo methodInfo = info.getMethodInfo(method);
        assertNotNull("Could not find methodInfo for: " + method, methodInfo);

        ExchangePattern actualPattern = methodInfo.getPattern();
        assertEquals("Pattern for: " + method, expectedPattern, actualPattern);

        //System.out.println("Method: " + method + " has pattern: " + actualPattern);
    }

    public interface Foo {
        void inOutMethod();

        @OneWay
        void inOnlyMethod();

        @OneWay(ExchangePattern.RobustInOnly)
        void robustInOnlyMethod();
    }
}
