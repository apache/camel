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

import org.apache.camel.Body;
import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.Test;

public class BeanParameterInfoTest extends ContextTestSupport {

    protected CamelContext camelContext = new DefaultCamelContext();

    @Test
    public void testMethodPatternUsingMethodAnnotations() throws Exception {
        Class<?> foo = Foo.class.getClass();
        ParameterInfo info = new ParameterInfo(1, foo.getClass(), foo.getAnnotations(), null);

        assertNotNull(info);
        assertNotNull(info.toString());

        assertEquals(1, info.getIndex());
        assertEquals(foo, info.getType());
        assertNull(info.getExpression());
        assertNotNull(info.getAnnotations());
    }

    @SuppressWarnings("unused")
    private static class Foo {

        public String hello(@Body String body) {
            return "Hello " + body;
        }
    }

}
