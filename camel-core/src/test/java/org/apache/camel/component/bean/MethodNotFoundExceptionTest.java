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

import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;

public class MethodNotFoundExceptionTest extends TestCase {

    @SuppressWarnings("rawtypes")
    public void testToString() {
        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        MyDummyBean bean = new MyDummyBean();
        String methodName = "foo";
        List<Class> parameterTypes = new ArrayList<Class>();
        parameterTypes.add(String.class);
        parameterTypes.add(Long.class);
        MethodNotFoundException exception = 
            new MethodNotFoundException(exchange, bean, methodName, parameterTypes);

        assertSame(exchange, exception.getExchange());
        assertSame(bean, exception.getBean());
        assertSame(methodName, exception.getMethodName());
        assertSame(parameterTypes, exception.getParameterTypes());
        assertEquals(
                "Method with name: foo and parameter types: [class java.lang.String, "
                + "class java.lang.Long] not found on bean: "
                + "org.apache.camel.component.bean.MyDummyBean. Exchange[null]",
                exception.getMessage());
    }
}