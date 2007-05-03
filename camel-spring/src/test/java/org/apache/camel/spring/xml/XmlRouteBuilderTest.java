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
package org.apache.camel.spring.xml;

import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilderTest;
import org.apache.camel.processor.DelegateProcessor;
import org.apache.camel.spring.SpringCamelContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.List;

/**
 * TODO: re-implement the route building logic using spring and
 * then test it by overriding the buildXXX methods in the RouteBuilderTest
 *
 * @version $Revision: 520164 $
 */
public class XmlRouteBuilderTest extends RouteBuilderTest {
    private static ClassPathXmlApplicationContext applicationContext;
    private static boolean closeContext = false;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        if (applicationContext == null) {
            applicationContext = new ClassPathXmlApplicationContext("org/apache/camel/spring/builder/spring_route_builder_test.xml");
        }
    }

    @Override
    protected void tearDown() throws Exception {
        if (closeContext) {
            applicationContext.close();
            applicationContext = null;
        }
        super.tearDown();
    }

    @Override
    @SuppressWarnings("unchecked")
    protected List<Route> buildSimpleRoute() {
        return getRoutesFromContext("buildSimpleRoute");
    }

    @Override
    protected List<Route> buildCustomProcessor() {
        myProcessor = (Processor) applicationContext.getBean("myProcessor");
        return getRoutesFromContext("buildCustomProcessor");
    }

    @Override
    protected List<Route> buildCustomProcessorWithFilter() {
        myProcessor = (Processor) applicationContext.getBean("myProcessor");
        return getRoutesFromContext("buildCustomProcessorWithFilter");
    }

    @Override
    protected List<Route> buildRouteWithInterceptor() {
        interceptor1 = (DelegateProcessor) applicationContext.getBean("interceptor1");
        interceptor2 = (DelegateProcessor) applicationContext.getBean("interceptor2");
        return getRoutesFromContext("buildRouteWithInterceptor");
    }

    @Override
    protected List<Route> buildSimpleRouteWithHeaderPredicate() {
        return getRoutesFromContext("buildSimpleRouteWithHeaderPredicate");
    }

    @Override
    protected List<Route> buildSimpleRouteWithChoice() {
        return getRoutesFromContext("buildSimpleRouteWithChoice");
    }

    @Override
    protected List<Route> buildWireTap() {
        return getRoutesFromContext("buildWireTap");
    }

    @Override
    protected List<Route> buildDynamicRecipientList() {
        return getRoutesFromContext("buildDynamicRecipientList");
    }

    @Override
    protected List<Route> buildStaticRecipientList() {
        return getRoutesFromContext("buildStaticRecipientList");
    }

    @Override
    protected List<Route> buildSplitter() {
        return getRoutesFromContext("buildSplitter");
    }

    @Override
    protected List<Route> buildIdempotentConsumer() {
        return getRoutesFromContext("buildIdempotentConsumer");
    }

    @Override
    public void testIdempotentConsumer() throws Exception {
        // TODO
    }

    protected List<Route> getRoutesFromContext(String name) {
        SpringCamelContext context = (SpringCamelContext) applicationContext.getBean(name);
        assertNotNull("No Camel Context for name: " + name, context);
        List<Route> routes = context.getRoutes();
        assertNotNull("No routes available for context: " + name, routes);
        return routes;
    }
}
