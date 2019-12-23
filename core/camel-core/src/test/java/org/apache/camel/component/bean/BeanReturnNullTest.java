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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

/**
 *
 */
public class BeanReturnNullTest extends ContextTestSupport {

    @Test
    public void testReturnBean() throws Exception {
        MyBean out = template.requestBody("direct:start", "Camel", MyBean.class);
        assertNotNull(out);
        assertEquals("Camel", out.getName());
    }

    @Test
    public void testReturnNull() throws Exception {
        Object out = template.requestBody("direct:start", "foo");
        assertNull(out);
    }

    @Test
    public void testReturnNullMyBean() throws Exception {
        MyBean out = template.requestBody("direct:start", "foo", MyBean.class);
        assertNull(out);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").bean(BeanReturnNullTest.class, "doSomething");
            }
        };
    }

    public MyBean doSomething(String body) {
        if ("foo".equals(body)) {
            return null;
        } else {
            return new MyBean(body);
        }
    }

    public static final class MyBean {

        public String name;

        public MyBean(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

}
