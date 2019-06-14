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
package org.apache.camel.support;

import org.apache.camel.ContextTestSupport;
import org.junit.Test;

/**
 * Unit test for PropertyBindingSupport
 */
public class PropertyBindingSupportAutowireClasspathTest extends ContextTestSupport {

    @Test
    public void testAutowireProperties() throws Exception {
        Foo foo = new Foo();

        PropertyBindingSupport.bindProperty(context, foo, "name", "James");
        PropertyBindingSupport.autowireInterfacePropertiesFromClasspath(context, foo);
        PropertyBindingSupport.bindProperty(context, foo, "my-bar.name", "Thirsty Bear");
        PropertyBindingSupport.bindProperty(context, foo, "my-bar.city", "San Francisco");

        assertEquals("James", foo.getName());
        assertEquals("Thirsty Bear", foo.getMyBar().getName());
        assertEquals("San Francisco", foo.getMyBar().getCity());
    }

    public static class Foo {
        private String name;
        private MyBarInterface myBar;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public MyBarInterface getMyBar() {
            return myBar;
        }

        public void setMyBar(MyBarInterface myBar) {
            this.myBar = myBar;
        }
    }


}

