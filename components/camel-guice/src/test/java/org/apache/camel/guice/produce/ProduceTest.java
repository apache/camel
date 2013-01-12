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
package org.apache.camel.guice.produce;

import org.apache.camel.Produce;
import org.apache.camel.guice.CamelModuleWithMatchingRoutes;
import org.apache.camel.guice.testing.UseModule;
import org.apache.camel.guice.testing.junit4.GuiceyJUnit4;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @version 
 */
@RunWith(GuiceyJUnit4.class)
@UseModule(ProduceTest.TestModule.class)
public class ProduceTest {

    @Produce(uri = "direct:myService")
    protected MyListener producer;

    @Test
    public void testInvokeService() throws Exception {
        // lets send a message
        String actual = producer.sayHello("James");
        Assert.assertEquals("response", "Hello James", actual);
    }

    public static class TestModule extends CamelModuleWithMatchingRoutes {
        @Override
        protected void configure() {
            super.configure();

            bind(MyListenerService.class).asEagerSingleton();
        }
    }
}