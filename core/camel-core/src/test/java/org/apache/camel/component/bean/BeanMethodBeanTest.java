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

import org.apache.camel.TestSupport;
import org.junit.Test;

public class BeanMethodBeanTest extends TestSupport {

    @Test
    public void testBeanMethod() throws Exception {
        Method method = MyFooBean.class.getMethod("hello", String.class);

        MethodBean mb = new MethodBean();
        mb.setName("hello");
        mb.setType(MyFooBean.class);
        mb.setParameterTypes(method.getParameterTypes());

        assertEquals("hello", mb.getName());
        assertEquals(method, mb.getMethod());
        assertNotNull(mb.getParameterTypes());
        assertEquals(MyFooBean.class, mb.getType());
    }

}
