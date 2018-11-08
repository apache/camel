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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.DefaultMessage;
import org.junit.Test;

/**
 * @version 
 */
public class BeanInfoOverloadedTest extends ContextTestSupport {

    @Test
    public void testBeanInfoOverloaded() throws Exception {
        BeanInfo beanInfo = new BeanInfo(context, Bean.class);

        Message message = new DefaultMessage(context);
        message.setBody(new RequestB());
        Exchange exchange = new DefaultExchange(context);
        exchange.setIn(message);

        MethodInvocation methodInvocation = beanInfo.createInvocation(new Bean(), exchange);
        Method method = methodInvocation.getMethod();

        assertEquals("doSomething", method.getName());

        assertEquals(RequestB.class, method.getGenericParameterTypes()[0]);
    }

    class Bean {
        public void doSomething(RequestA request) {
        }

        public void doSomething(RequestB request) {
        }
    }

    class RequestA {
        public int i;
    }

    class RequestB {
        public String s;
    }

}
