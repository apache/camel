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
package org.apache.camel.spring.spi;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.spring.SpringCamelContext;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class SpringInjectorFactoryMethodTest {

    @Test
    void factoryMethodBeanGetsCamelContext() throws Exception {
        try (AnnotationConfigApplicationContext appCtx = new AnnotationConfigApplicationContext()) {
            appCtx.refresh();
            SpringCamelContext camel = new SpringCamelContext(appCtx);
            camel.start();
            try {
                MyBean bean = camel.getInjector().newInstance(MyBean.class, "createMyBean");
                assertNotNull(bean, "Factory method should return a non-null bean");
                assertNotNull(bean.getCamelContext(), "CamelContext should be injected into CamelContextAware bean");
                assertSame(camel, bean.getCamelContext());
            } finally {
                camel.stop();
            }
        }
    }

    @Test
    void factoryMethodWithFactoryClassGetsCamelContext() throws Exception {
        try (AnnotationConfigApplicationContext appCtx = new AnnotationConfigApplicationContext()) {
            appCtx.refresh();
            SpringCamelContext camel = new SpringCamelContext(appCtx);
            camel.start();
            try {
                MyBean bean = camel.getInjector().newInstance(MyBean.class, MyBean.class, "createMyBean");
                assertNotNull(bean, "Factory method should return a non-null bean");
                assertNotNull(bean.getCamelContext(), "CamelContext should be injected into CamelContextAware bean");
                assertSame(camel, bean.getCamelContext());
            } finally {
                camel.stop();
            }
        }
    }

    public static class MyBean implements CamelContextAware {
        private CamelContext camelContext;

        public static MyBean createMyBean() {
            return new MyBean();
        }

        @Override
        public CamelContext getCamelContext() {
            return camelContext;
        }

        @Override
        public void setCamelContext(CamelContext camelContext) {
            this.camelContext = camelContext;
        }
    }
}
