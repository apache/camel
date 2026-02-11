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
package org.apache.camel.main;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.PropertyBindingSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit test for PropertyBindingSupport
 */
public class PropertyBindingSupportConstructorTest {

    @Test
    public void testConstructor() {
        CamelContext context = new DefaultCamelContext();

        context.start();

        MyApp target = new MyApp();

        PropertyBindingSupport.build()
                .withCamelContext(context)
                .withTarget(target)
                .withProperty("name", "Donald")
                .withProperty("counter",
                        "#class:" + AtomicInteger.class.getName() + "(1)")
                .withRemoveParameters(false).bind();

        assertEquals("Donald", target.getName());
        assertEquals(1, target.getCounter().get());

        context.stop();
    }

    @Test
    public void testConstructorPlaceholder() {
        CamelContext context = new DefaultCamelContext();

        context.getPropertiesComponent().addInitialProperty("initVal", "123");

        context.start();

        MyApp target = new MyApp();

        PropertyBindingSupport.build()
                .withCamelContext(context)
                .withTarget(target)
                .withProperty("name", "Donald")
                .withProperty("counter",
                        "#class:" + AtomicInteger.class.getName() + "({{initVal}})")
                .withRemoveParameters(false).bind();

        assertEquals("Donald", target.getName());
        assertEquals(123, target.getCounter().get());

        context.stop();
    }

    public static class MyApp {

        private String name;
        private AtomicInteger counter;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public AtomicInteger getCounter() {
            return counter;
        }

        public void setCounter(AtomicInteger counter) {
            this.counter = counter;
        }
    }

}
