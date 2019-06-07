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

import org.apache.camel.BeanInject;
import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.PropertyInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.direct.DirectComponent;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.seda.BlockingQueueFactory;
import org.apache.camel.component.seda.PriorityBlockingQueueFactory;
import org.apache.camel.component.seda.SedaComponent;
import org.apache.camel.model.ModelCamelContext;
import org.junit.Assert;
import org.junit.Test;

public class MainIoCTest extends Assert {

    @Test
    public void testMainIoC() throws Exception {
        // use configuration class
        Main main = new Main();
        // add the configuration
        main.addConfigurationClass(MyConfiguration.class);
        // add as class so we get IoC
        main.addRouteBuilder(MyRouteBuilder.class);
        // manually bind
        main.bind("myBar", new MyBar());

        // should be null before init
        assertNull(main.getCamelContext());
        // for testing that we can init camel and it has loaded configuration and routes and whatnot
        main.init();
        // and now its created
        assertNotNull(main.getCamelContext());
        // should be 1 route model
        assertEquals(1, main.getCamelContext().adapt(ModelCamelContext.class).getRouteDefinitions().size());
        // and the configuration should have registered beans
        assertNotNull(main.getCamelContext().getRegistry().lookupByName("MyCoolBean"));
        assertEquals("Tiger", main.getCamelContext().getRegistry().lookupByName("coolStuff"));

        // start it
        main.start();

        CamelContext camelContext = main.getCamelContext();

        assertNotNull(camelContext);

        MockEndpoint endpoint = camelContext.getEndpoint("mock:results", MockEndpoint.class);
        endpoint.expectedBodiesReceived("World");

        main.getCamelTemplate().sendBody("direct:start", "<message>1</message>");

        endpoint.assertIsSatisfied();

        // should also auto-configure direct/seda components from application.properties
        SedaComponent seda = camelContext.getComponent("seda", SedaComponent.class);
        assertEquals(500, seda.getQueueSize());
        assertEquals(2, seda.getConcurrentConsumers());
        DirectComponent direct = camelContext.getComponent("direct", DirectComponent.class);
        assertEquals(1234, direct.getTimeout());

        // should have called the configure class
        assertEquals("123", camelContext.getGlobalOptions().get("foo"));

        // and seda should have been auto-configured by type
        Object qf = seda.getDefaultQueueFactory();
        assertNotNull(qf);
        assertTrue(qf instanceof PriorityBlockingQueueFactory);

        MyConfiguration.MyCoolBean mcb = (MyConfiguration.MyCoolBean) camelContext.getRegistry().lookupByName("MyCoolBean");
        assertNotNull(mcb);
        assertEquals("Tiger", mcb.getName());

        Object cool = camelContext.getRegistry().lookupByName("coolStuff");
        assertNotNull(cool);
        assertEquals("Tiger", cool);

        main.stop();
    }

    public static class MyBar {
        // noop
    }

    public static class MyConfiguration {

        @BeanInject
        private CamelContext camel;

        @BindToRegistry
        public static class MyCoolBean {

            private String name = "Tiger";

            public String getName() {
                return name;
            }

            public void setName(String name) {
                this.name = name;
            }
        }

        @BindToRegistry
        public BlockingQueueFactory queueFactory(CamelContext myCamel) {
            // we can optionally include camel context as parameter
            Assert.assertNotNull(myCamel);
            return new PriorityBlockingQueueFactory();
        }

        @BindToRegistry("coolStuff")
        public String cool(@BeanInject MyCoolBean cool,
                           @PropertyInject(value = "magic", defaultValue = "456") int num,
                           @BeanInject("myBar") MyBar bar) {
            // should lookup MyCoolBean type from the registry and find the property
            Assert.assertNotNull(cool);
            Assert.assertEquals(456, num);
            Assert.assertNotNull(bar);
            return cool.getName();
        }

        public void configure() {
            camel.getGlobalOptions().put("foo", "123");
        }
    }

    public static class MyRouteBuilder extends RouteBuilder {

        // properties is automatic loaded from classpath:application.properties
        // so we should be able to inject this field

        @PropertyInject(value = "hello")
        private String hello;

        @Override
        public void configure() throws Exception {
            from("direct:start").transform().constant(hello).to("mock:results");
        }
    }
}
