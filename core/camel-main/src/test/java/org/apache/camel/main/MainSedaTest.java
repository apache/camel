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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.seda.SedaComponent;
import org.junit.Assert;
import org.junit.Test;

public class MainSedaTest extends Assert {

    @Test
    public void testSedaMain() throws Exception {
        Main main = new Main();
        main.addRoutesBuilder(new MyRouteBuilder());
        main.addProperty("camel.component.seda.defaultQueueFactory", "#class:org.apache.camel.main.MySedaBlockingQueueFactory");
        main.addProperty("camel.component.seda.defaultQueueFactory.counter", "123");
        main.start();

        CamelContext camelContext = main.getCamelContext();
        assertNotNull(camelContext);

        SedaComponent seda = camelContext.getComponent("seda", SedaComponent.class);
        assertNotNull(seda);
        assertTrue(seda.getDefaultQueueFactory() instanceof MySedaBlockingQueueFactory);
        MySedaBlockingQueueFactory myBQF = (MySedaBlockingQueueFactory) seda.getDefaultQueueFactory();
        assertEquals(123, myBQF.getCounter());

        main.stop();
    }

    @Test
    public void testSedaAutowireFromRegistryMain() throws Exception {
        Main main = new Main();
        main.addRoutesBuilder(new MyRouteBuilder());
        main.addProperty("camel.beans.myqf", "#class:org.apache.camel.main.MySedaBlockingQueueFactory");
        main.addProperty("camel.beans.myqf.counter", "123");
        main.start();

        CamelContext camelContext = main.getCamelContext();
        assertNotNull(camelContext);

        // the keys will be lower-cased
        assertNotNull(camelContext.getRegistry().lookupByName("myqf"));

        // seda will autowire from registry and discover the custom qf and use it
        SedaComponent seda = camelContext.getComponent("seda", SedaComponent.class);
        assertNotNull(seda);
        assertTrue(seda.getDefaultQueueFactory() instanceof MySedaBlockingQueueFactory);
        MySedaBlockingQueueFactory myBQF = (MySedaBlockingQueueFactory) seda.getDefaultQueueFactory();
        assertEquals(123, myBQF.getCounter());

        main.stop();
    }

    public static class MyRouteBuilder extends RouteBuilder {
        @Override
        public void configure() throws Exception {
            from("direct:start").to("seda:foo");
        }
    }

}
