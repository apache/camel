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

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.PropertyInject;
import org.apache.camel.impl.engine.CamelPostProcessorHelper;
import org.apache.camel.spi.CamelBeanPostProcessor;
import org.apache.camel.spi.CamelBeanPostProcessorInjector;
import org.apache.camel.spi.CamelLogger;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.util.ReflectionHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class CamelBeanPostProcessorInjectorTest extends ContextTestSupport {

    private CamelBeanPostProcessor postProcessor;
    private CamelPostProcessorHelper helper;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        postProcessor = PluginHelper.getBeanPostProcessor(context);
        postProcessor.addCamelBeanPostProjectInjector(new MyInjector());
        helper = new CamelPostProcessorHelper(context);
    }

    private class MyInjector implements CamelBeanPostProcessorInjector {

        @Override
        public void onFieldInject(Field field, Object bean, String beanName) {
            if (field.getName().equals("foo")) {
                ReflectionHelper.setField(field, bean, "changed-foo");
            }
        }

        @Override
        public void onMethodInject(Method method, Object bean, String beanName) {
            if (method.getName().equals("createLogger")) {
                Object out = ObjectHelper.invokeMethod(method, bean, "changed-bar");
                context.getRegistry().bind(method.getName(), out);
            }
        }
    }

    public static class MyService {

        @PropertyInject(value = "myName", defaultValue = "Donald Duck")
        private String name;
        @PropertyInject(value = "myFoo", defaultValue = "myDefault")
        private String foo;

        public String getName() {
            return name;
        }

        public String getFoo() {
            return foo;
        }

        public CamelLogger createLogger(String name) {
            return new CamelLogger(name);
        }
    }

    @Test
    public void testBeanPostInjector() throws Exception {
        MyService service = new MyService();

        postProcessor.postProcessBeforeInitialization(service, "service");
        postProcessor.postProcessAfterInitialization(service, "service");

        Assertions.assertEquals("Donald Duck", service.getName());
        Assertions.assertEquals("changed-foo", service.getFoo());

        CamelLogger logger = (CamelLogger) context.getRegistry().lookupByName("createLogger");
        Assertions.assertNotNull(logger);
    }

}
