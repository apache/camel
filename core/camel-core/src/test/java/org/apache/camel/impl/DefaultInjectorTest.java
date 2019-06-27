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
package org.apache.camel.impl;

import org.apache.camel.CamelContext;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 */
public class DefaultInjectorTest extends Assert {

    @Test
    public void testDefaultInjector() throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.start();

        // use the injector (will use the default)
        // which should post process the bean to inject the @Produce
        MyBean bean = context.getInjector().newInstance(MyBean.class);

        Object reply = bean.doSomething("World");
        assertEquals("WorldWorld", reply);
    }

    @Test
    public void testDefaultInjectorFactory() throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.start();

        // use the injector (will use the default)
        MyOtherBean bean = context.getInjector().newInstance(MyOtherBean.class, "getInstance");

        Object reply = bean.doSomething("World");
        assertEquals("WorldWorld", reply);
    }

    public static class MyBean {

        @Produce("language:simple:${body}${body}")
        ProducerTemplate template;

        public Object doSomething(String body) {
            return template.requestBody(body);
        }
    }

    public static class MyOtherBean {

        private static MyOtherBean me = new MyOtherBean();

        public static MyOtherBean getInstance() {
            return me;
        }

        public Object doSomething(String body) {
            return body + body;
        }
    }

}
