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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class MainBeansConstructorTest {

    @Test
    public void testBindBeans() throws Exception {
        Main main = new Main();
        // create by class
        main.addProperty("camel.beans.address", "#class:org.apache.camel.main.MyAddress(90210, 'Somestreet 123')");
        main.addProperty("camel.beans.order", "#class:org.apache.camel.main.MyOrder('Acme', #bean:address)");
        // start
        main.start();

        CamelContext camelContext = main.getCamelContext();
        assertNotNull(camelContext);

        MyOrder order = camelContext.getRegistry().lookupByNameAndType("order", MyOrder.class);
        assertNotNull(order);

        assertEquals("Acme", order.getCompany());
        assertEquals(90210, order.getAddress().getZip());
        assertEquals("Somestreet 123", order.getAddress().getStreet());

        main.stop();
    }

}
