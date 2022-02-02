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
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class MainRoutesCollectorPackageScanTest {

    @Test
    public void testMainRoutesCollector() throws Exception {
        Main main = new Main();
        main.configure().withBasePackageScan("org.apache.camel.main.scan");
        main.start();

        CamelContext camelContext = main.getCamelContext();
        assertNotNull(camelContext);
        assertEquals(3, camelContext.getRoutes().size());

        MockEndpoint endpoint = camelContext.getEndpoint("mock:scan", MockEndpoint.class);
        endpoint.expectedBodiesReceived("Hello World");
        MockEndpoint endpoint2 = camelContext.getEndpoint("mock:dummy", MockEndpoint.class);
        endpoint2.expectedBodiesReceived("Bye World");
        MockEndpoint endpoint3 = camelContext.getEndpoint("mock:concrete", MockEndpoint.class);
        endpoint3.expectedBodiesReceived("Hola World");

        main.getCamelTemplate().sendBody("direct:scan", "Hello World");
        main.getCamelTemplate().sendBody("direct:dummy", "Bye World");
        main.getCamelTemplate().sendBody("direct:concrete", "Hola World");

        endpoint.assertIsSatisfied();
        endpoint2.assertIsSatisfied();
        endpoint3.assertIsSatisfied();

        // camel configuration should be scanned
        Assertions.assertEquals("true", camelContext.getGlobalOption("scanConfigured"));
        MyAddress adr = camelContext.getRegistry().lookupByNameAndType("address", MyAddress.class);
        Assertions.assertEquals(4444, adr.getZip());
        Assertions.assertEquals("Somestreet 123", adr.getStreet());

        // custom type converter should be scanned
        MyFoo foo = camelContext.getTypeConverter().convertTo(MyFoo.class, "Donald");
        assertEquals("Donald", foo.getName());

        main.stop();
    }

}
