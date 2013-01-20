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
package org.apache.camel.core.osgi;

import java.util.Map;

import org.apache.camel.core.osgi.test.MyService;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.Test;

public class ServiceRegistryTest extends CamelOsgiTestSupport {

    @Test
    public void camelContextFactoryServiceRegistryTest() throws Exception {
        
        DefaultCamelContext context = new OsgiDefaultCamelContext(getBundleContext());
        context.start();
        MyService myService = context.getRegistry().lookup(MyService.class.getName(), MyService.class);
        assertNotNull("MyService should not be null", myService);
        
        myService = context.getRegistry().lookup("test", MyService.class);
        assertNull("We should not get the MyService Object here", myService);

        Object service = context.getRegistry().lookup(MyService.class.getName());
        assertNotNull("MyService should not be null", service);
        assertTrue("It should be the instance of MyService ", service instanceof MyService);

        Map<String, MyService> collection = context.getRegistry().lookupByType(MyService.class);
        assertNotNull("MyService should not be null", collection);
        assertNotNull("There should have one MyService.", collection.get(MyService.class.getName()));
        context.stop();
    }

}
