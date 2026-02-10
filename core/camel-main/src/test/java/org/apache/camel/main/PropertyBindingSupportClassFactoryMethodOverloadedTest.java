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

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.PropertyBindingSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for PropertyBindingSupport
 */
public class PropertyBindingSupportClassFactoryMethodOverloadedTest {

    @Test
    public void testFactoryPropertyOverloaded() {
        CamelContext context = new DefaultCamelContext();

        context.start();

        MyApp target = new MyApp();

        PropertyBindingSupport.build()
                .withCamelContext(context)
                .withTarget(target)
                .withProperty("name", "Donald")
                .withProperty("myBinding",
                        "#class:" + MyBinding.class.getName() + "#createBinding(true,'scott')")
                .withRemoveParameters(false).bind();

        assertEquals("Donald", target.getName());
        assertTrue(target.getMyBinding().isFlag());
        assertFalse(target.getMyBinding().isFlag2());
        assertEquals("scott", target.getMyBinding().getUser());

        context.stop();
    }

    @Test
    public void testFactoryPropertyOverloadedTwo() {
        CamelContext context = new DefaultCamelContext();

        context.start();

        MyApp target = new MyApp();

        PropertyBindingSupport.build()
                .withCamelContext(context)
                .withTarget(target)
                .withProperty("name", "Donald")
                .withProperty("myBinding",
                        "#class:" + MyBinding.class.getName() + "#createBinding(true,true)")
                .withRemoveParameters(false).bind();

        assertEquals("Donald", target.getName());
        assertTrue(target.getMyBinding().isFlag());
        assertTrue(target.getMyBinding().isFlag2());
        assertEquals("anonymous", target.getMyBinding().getUser());

        context.stop();
    }

    public static class MyApp {

        private String name;
        private MyBinding myBinding;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public MyBinding getMyBinding() {
            return myBinding;
        }

        public void setMyBinding(MyBinding myBinding) {
            this.myBinding = myBinding;
        }
    }

    public static class MyBinding {

        private boolean flag;
        private boolean flag2;
        private String user;

        public static MyBinding createBinding(boolean flag, String user) {
            MyBinding binding = new MyBinding();
            binding.flag = flag;
            binding.user = user;
            return binding;
        }

        public static MyBinding createBinding(boolean flag, boolean flag2) {
            MyBinding binding = new MyBinding();
            binding.flag = flag;
            binding.flag2 = flag2;
            binding.user = "anonymous";
            return binding;
        }

        public boolean isFlag() {
            return flag;
        }

        public boolean isFlag2() {
            return flag2;
        }

        public String getUser() {
            return user;
        }
    }

}
