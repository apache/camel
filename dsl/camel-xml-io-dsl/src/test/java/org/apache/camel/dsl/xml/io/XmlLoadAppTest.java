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
package org.apache.camel.dsl.xml.io;

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dsl.xml.io.beans.GreeterMessage;
import org.apache.camel.dsl.xml.io.beans.MyDestroyBean;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.RoutesLoader;
import org.apache.camel.support.PluginHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class XmlLoadAppTest {

    @Test
    public void testLoadCamelAppFromXml() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.start();

            // load route from XML and add them both to the existing camel context
            // only first XML declares component scanning (to put beans into the registry)
            String[] contexts = new String[] {
                    "camel-app1.xml",
                    "camel-app2.xml"
            };
            for (String r : contexts) {
                Resource resource = PluginHelper.getResourceLoader(context).resolveResource(
                        "/org/apache/camel/dsl/xml/io/" + r);

                RoutesLoader routesLoader = PluginHelper.getRoutesLoader(context);
                routesLoader.preParseRoute(resource, false);
                routesLoader.loadRoutes(resource);
            }

            assertNotNull(context.getRoute("r1"), "Loaded r1 route should be there");
            assertNotNull(context.getRoute("r2"), "Loaded r2 route should be there");
            assertEquals(2, context.getRoutes().size());

            // tweak bean in registry
            GreeterMessage gm = context.getRegistry().findSingleByType(GreeterMessage.class);
            gm.setMsg("Hello");

            // test that loaded route works
            MockEndpoint y1 = context.getEndpoint("mock:y1", MockEndpoint.class);
            y1.expectedBodiesReceived("Hello World");
            context.createProducerTemplate().sendBody("direct:x1", "I'm World");
            y1.assertIsSatisfied();

            MockEndpoint y2 = context.getEndpoint("mock:y2", MockEndpoint.class);
            y2.expectedBodiesReceived("Hello World");
            context.createProducerTemplate().sendBody("direct:x2", "I'm World");
            y2.assertIsSatisfied();
        }
    }

    @Test
    public void testLoadCamelAppWithBeansAndDI() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.start();

            // camel-app3 registers two beans and 2nd one uses @BeanInject on first one

            Resource resource = PluginHelper.getResourceLoader(context).resolveResource(
                    "/org/apache/camel/dsl/xml/io/camel-app3.xml");

            RoutesLoader routesLoader = PluginHelper.getRoutesLoader(context);
            routesLoader.preParseRoute(resource, false);
            routesLoader.loadRoutes(resource);

            assertNotNull(context.getRoute("r3"), "Loaded r3 route should be there");
            assertEquals(1, context.getRoutes().size());

            // test that loaded route works
            MockEndpoint y3 = context.getEndpoint("mock:y3", MockEndpoint.class);
            y3.expectedBodiesReceived("Hello World");
            context.createProducerTemplate().sendBody("direct:x3", "I'm World");
            y3.assertIsSatisfied();
        }
    }

    @Test
    public void testLoadCamelAppWithBeansAndFlattenedProperties() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.start();

            // camel-app4 registers one bean, where its dependency is created from the flattened properties
            // and using org.apache.camel.spi.Injector.newInstance()

            Resource resource = PluginHelper.getResourceLoader(context).resolveResource(
                    "/org/apache/camel/dsl/xml/io/camel-app4.xml");

            RoutesLoader routesLoader = PluginHelper.getRoutesLoader(context);
            routesLoader.preParseRoute(resource, false);
            routesLoader.loadRoutes(resource);

            assertNotNull(context.getRoute("r4"), "Loaded r4 route should be there");
            assertEquals(1, context.getRoutes().size());

            // test that loaded route works
            MockEndpoint y4 = context.getEndpoint("mock:y4", MockEndpoint.class);
            y4.expectedBodiesReceived("Hello World");
            context.createProducerTemplate().sendBody("direct:x4", "I'm World");
            y4.assertIsSatisfied();
        }
    }

    @Test
    public void testLoadCamelAppWithBeanCtr() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.start();

            Resource resource = PluginHelper.getResourceLoader(context).resolveResource(
                    "/org/apache/camel/dsl/xml/io/camel-app5.xml");

            RoutesLoader routesLoader = PluginHelper.getRoutesLoader(context);
            routesLoader.preParseRoute(resource, false);
            routesLoader.loadRoutes(resource);

            assertNotNull(context.getRoute("r5"), "Loaded r5 route should be there");
            assertEquals(1, context.getRoutes().size());

            // test that loaded route works
            MockEndpoint y5 = context.getEndpoint("mock:y5", MockEndpoint.class);
            y5.expectedBodiesReceived("Hello World. I am Camel and 42 years old!");
            context.createProducerTemplate().sendBody("direct:x5", "World");
            y5.assertIsSatisfied();
        }
    }

    @Test
    public void testLoadCamelAppWithBeanFactoryMethod() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.start();

            Resource resource = PluginHelper.getResourceLoader(context).resolveResource(
                    "/org/apache/camel/dsl/xml/io/camel-app6.xml");

            RoutesLoader routesLoader = PluginHelper.getRoutesLoader(context);
            routesLoader.preParseRoute(resource, false);
            routesLoader.loadRoutes(resource);

            assertNotNull(context.getRoute("r6"), "Loaded r6 route should be there");
            assertEquals(1, context.getRoutes().size());

            // test that loaded route works
            MockEndpoint y6 = context.getEndpoint("mock:y6", MockEndpoint.class);
            y6.expectedBodiesReceived("Hello Moon. I am Camel and 43 years old!");
            context.createProducerTemplate().sendBody("direct:x6", "Moon");
            y6.assertIsSatisfied();
        }
    }

    @Test
    public void testLoadCamelAppWithBeanFactoryBeanAndMethod() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.start();

            Resource resource = PluginHelper.getResourceLoader(context).resolveResource(
                    "/org/apache/camel/dsl/xml/io/camel-app7.xml");

            RoutesLoader routesLoader = PluginHelper.getRoutesLoader(context);
            routesLoader.preParseRoute(resource, false);
            routesLoader.loadRoutes(resource);

            assertNotNull(context.getRoute("r7"), "Loaded r7 route should be there");
            assertEquals(1, context.getRoutes().size());

            // test that loaded route works
            MockEndpoint y7 = context.getEndpoint("mock:y7", MockEndpoint.class);
            y7.expectedBodiesReceived("Hello Pluto. I am Camel and 44 years old!");
            context.createProducerTemplate().sendBody("direct:x7", "Pluto");
            y7.assertIsSatisfied();
        }
    }

    @Test
    public void testLoadCamelAppWithBeanInitDestroy() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.start();

            assertFalse(MyDestroyBean.initCalled.get());
            assertFalse(MyDestroyBean.destroyCalled.get());

            Resource resource = PluginHelper.getResourceLoader(context).resolveResource(
                    "/org/apache/camel/dsl/xml/io/camel-app8.xml");

            RoutesLoader routesLoader = PluginHelper.getRoutesLoader(context);
            routesLoader.preParseRoute(resource, false);
            routesLoader.loadRoutes(resource);

            assertNotNull(context.getRoute("r8"), "Loaded r8 route should be there");
            assertEquals(1, context.getRoutes().size());

            // test that loaded route works
            MockEndpoint y8 = context.getEndpoint("mock:y8", MockEndpoint.class);
            y8.expectedBodiesReceived("Hello Neptun. I am Camel and 45 years old!");
            context.createProducerTemplate().sendBody("direct:x8", "Neptun");
            y8.assertIsSatisfied();

            assertTrue(MyDestroyBean.initCalled.get());
            assertFalse(MyDestroyBean.destroyCalled.get());

            context.stop();

            assertTrue(MyDestroyBean.initCalled.get());
            assertTrue(MyDestroyBean.destroyCalled.get());
        }
    }

    @Test
    public void testLoadCamelAppWithBeanScript() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.start();

            Resource resource = PluginHelper.getResourceLoader(context).resolveResource(
                    "/org/apache/camel/dsl/xml/io/camel-app9.xml");

            RoutesLoader routesLoader = PluginHelper.getRoutesLoader(context);
            routesLoader.preParseRoute(resource, false);
            routesLoader.loadRoutes(resource);

            assertNotNull(context.getRoute("r9"), "Loaded r9 route should be there");
            assertEquals(1, context.getRoutes().size());

            // test that loaded route works
            MockEndpoint y9 = context.getEndpoint("mock:y9", MockEndpoint.class);
            y9.expectedBodiesReceived("Hi World from groovy Uranus");
            context.createProducerTemplate().sendBody("direct:x9", "I'm Uranus");
            y9.assertIsSatisfied();

            context.stop();
        }
    }

    @Test
    public void testLoadCamelAppWithBeanBuilderClass() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.start();

            Resource resource = PluginHelper.getResourceLoader(context).resolveResource(
                    "/org/apache/camel/dsl/xml/io/camel-app10.xml");

            RoutesLoader routesLoader = PluginHelper.getRoutesLoader(context);
            routesLoader.preParseRoute(resource, false);
            routesLoader.loadRoutes(resource);

            assertNotNull(context.getRoute("r10"), "Loaded r10 route should be there");
            assertEquals(1, context.getRoutes().size());

            // test that loaded route works
            MockEndpoint y10 = context.getEndpoint("mock:y10", MockEndpoint.class);
            y10.expectedBodiesReceived("Hi World. I am Camel and 44 years old!");
            context.createProducerTemplate().sendBody("direct:x10", "Hi");
            y10.assertIsSatisfied();

            context.stop();
        }
    }

}
