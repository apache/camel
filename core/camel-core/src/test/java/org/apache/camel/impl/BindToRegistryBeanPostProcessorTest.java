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

import org.apache.camel.BeanInject;
import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.spi.CamelBeanPostProcessor;
import org.apache.camel.support.PluginHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class BindToRegistryBeanPostProcessorTest extends ContextTestSupport {

    // field
    @BindToRegistry(beanPostProcess = true)
    private FooService foo = new FooService();

    // method
    @BindToRegistry(beanPostProcess = true)
    public FooService myOtherFoo() {
        return new FooService();
    }

    @Test
    public void testPostProcessor() throws Exception {
        // bean post processing dont run on ContextTestSupport
        CamelBeanPostProcessor cbpp = PluginHelper.getBeanPostProcessor(context);
        cbpp.postProcessBeforeInitialization(this, "this");
        cbpp.postProcessAfterInitialization(this, "this");

        assertNotNull(foo);
        assertSame(context, foo.getCamelContext());

        FooService other = (FooService) context.getRegistry().lookupByName("myOtherFoo");
        assertNotNull(other);
        assertSame(context, other.getCamelContext());
    }

    public static class FooService {

        @BeanInject
        private CamelContext camelContext;

        public CamelContext getCamelContext() {
            return camelContext;
        }

        public void setCamelContext(CamelContext camelContext) {
            this.camelContext = camelContext;
        }
    }
}
