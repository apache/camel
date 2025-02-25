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

import java.util.function.Supplier;

import org.apache.camel.BindToRegistry;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.spi.CamelBeanPostProcessor;
import org.apache.camel.support.PluginHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class BindToRegistrySupplierBeanInitDestroyMethodTest extends ContextTestSupport {

    @BindToRegistry(initMethod = "start", destroyMethod = "stop")
    public Supplier<FooService> myFoo() {
        return () -> new FooService("World");
    }

    @Test
    public void testStop() throws Exception {
        // bean post processing dont run on ContextTestSupport
        CamelBeanPostProcessor cbpp = PluginHelper.getBeanPostProcessor(context);
        cbpp.postProcessBeforeInitialization(this, "this");
        cbpp.postProcessAfterInitialization(this, "this");

        FooService foo = context.getRegistry().lookupByNameAndType("myFoo", FooService.class);
        assertNotNull(foo);
        assertEquals("Started World", foo.getMessage());

        // stop camel should trigger destroy
        context.stop();

        assertEquals("Stopped", foo.getMessage());
    }

    @Test
    public void testUnbind() throws Exception {
        // bean post processing dont run on ContextTestSupport
        CamelBeanPostProcessor cbpp = PluginHelper.getBeanPostProcessor(context);
        cbpp.postProcessBeforeInitialization(this, "this");
        cbpp.postProcessAfterInitialization(this, "this");

        FooService foo = context.getRegistry().lookupByNameAndType("myFoo", FooService.class);
        assertNotNull(foo);
        assertEquals("Started World", foo.getMessage());

        // unbind should trigger destroy
        context.getRegistry().unbind("myFoo");
        assertEquals("Stopped", foo.getMessage());
    }

    public static class FooService {

        private String message;

        public FooService(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }

        public void start() {
            this.message = "Started " + message;
        }

        public void stop() {
            this.message = "Stopped";
        }

    }
}
