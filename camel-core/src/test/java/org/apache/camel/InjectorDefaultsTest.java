/**
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
package org.apache.camel;

import org.apache.camel.impl.DefaultInjector;
import org.apache.camel.spi.Injector;

/**
 * @version 
 */
public class InjectorDefaultsTest extends ContextTestSupport {

    public void testInjectorIsDefaultByDefault() throws Exception {
        Injector injector = context.getInjector();
        assertIsInstanceOf(DefaultInjector.class, injector);
    }

    public void testNewInstance() throws Exception {
        Injector injector = context.getInjector();

        MyFoo foo = injector.newInstance(MyFoo.class);
        foo.setName("Claus");

        MyFoo foo2 = injector.newInstance(MyFoo.class);
        assertNotSame(foo, foo2);

        assertEquals("Claus", foo.getName());
        assertNull(foo2.getName());
    }

    public void testSharedInstance() throws Exception {
        Injector injector = context.getInjector();

        MyBarSingleton bar = injector.newInstance(MyBarSingleton.class, new MyBarSingleton());
        bar.setName("Claus");

        MyBarSingleton bar2 = injector.newInstance(MyBarSingleton.class, bar);
        assertSame(bar, bar2);

        assertEquals("Claus", bar.getName());
        assertEquals("Claus", bar2.getName());
    }


}
