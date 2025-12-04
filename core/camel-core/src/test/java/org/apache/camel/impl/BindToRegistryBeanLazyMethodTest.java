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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.apache.camel.BindToRegistry;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.spi.CamelBeanPostProcessor;
import org.apache.camel.support.PluginHelper;
import org.junit.jupiter.api.Test;

public class BindToRegistryBeanLazyMethodTest extends ContextTestSupport {

    private String hello = "Hello World";

    @BindToRegistry(lazy = false)
    public FooService myEager() {
        return new FooService(hello);
    }

    @BindToRegistry(lazy = true)
    public FooService myLazy() {
        return new FooService(hello);
    }

    @Test
    public void testLazy() throws Exception {
        // bean post processing dont run on ContextTestSupport
        CamelBeanPostProcessor cbpp = PluginHelper.getBeanPostProcessor(context);
        cbpp.postProcessBeforeInitialization(this, "this");
        cbpp.postProcessAfterInitialization(this, "this");

        // change message which should only affect lazy
        hello = "Bye World";

        FooService eager = context.getRegistry().lookupByNameAndType("myEager", FooService.class);
        assertNotNull(eager);
        assertEquals("Hello World", eager.getMessage());

        FooService lazy = context.getRegistry().lookupByNameAndType("myLazy", FooService.class);
        assertNotNull(lazy);
        assertEquals("Bye World", lazy.getMessage());
    }

    public static class FooService {

        private final String message;

        public FooService(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }
}
