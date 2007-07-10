/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.spring.model;

import org.apache.camel.Route;
import org.apache.camel.spring.model.language.ExpressionType;

import javax.xml.bind.JAXBException;
import java.util.List;

/**
 * @version $Revision: 1.1 $
 */
public class XmlParseTest extends XmlTestSupport {
    public void testParseExample1Xml() throws Exception {
        RouteType route = assertOneRoute("example1.xml");
        assertFrom(route, "seda:a");
        assertTo(route, "seda:b");
    }

    public void testParseProcessorXml() throws Exception {
        RouteType route = assertOneRoute("processor.xml");
        assertFrom(route, "seda:a");
        assertProcessor(route, "myProcessor");
    }

    public void testParseProcessorWithFilterXml() throws Exception {
        RouteType route = assertOneRoute("processorWithFilter.xml");
        assertFrom(route, "seda:a");
        FilterType filter = assertFilter(route);
        assertExpression(filter.getExpression(), "juel", "in.header.foo == 'bar'");
    }

    public void testParseProcessorWithHeaderFilterXml() throws Exception {
        RouteType route = assertOneRoute("processorWithHeaderFilter.xml");
        assertFrom(route, "seda:a");
        FilterType filter = assertFilter(route);
        assertExpression(filter.getExpression(), "header", "foo");
    }

    public void testParseProcessorWithElFilterXml() throws Exception {
        RouteType route = assertOneRoute("processorWithElFilter.xml");
        assertFrom(route, "seda:a");
        FilterType filter = assertFilter(route);
        assertExpression(filter.getExpression(), "el", "$in.header.foo == 'bar'");
    }

    public void testParseProcessorWithGroovyFilterXml() throws Exception {
        RouteType route = assertOneRoute("processorWithGroovyFilter.xml");
        assertFrom(route, "seda:a");
        FilterType filter = assertFilter(route);
        assertExpression(filter.getExpression(), "groovy", "in.headers.any { h -> h.startsWith('foo')}");
    }

    public void testParseRecipientListXml() throws Exception {
        RouteType route = assertOneRoute("dynamicRecipientList.xml");
        assertFrom(route, "seda:a");
        RecipientListType node = assertRecipientList(route);
        assertExpression(node.getExpression(), "header", "foo");
    }

    public void testParseRouteWithInterceptorXml() throws Exception {
        RouteType route = assertOneRoute("routeWithInterceptor.xml");
        assertFrom(route, "seda:a");
        assertTo(route, "seda:d");
        assertInterceptorRefs(route, "interceptor1", "interceptor2");
    }

    // Implementation methods
    //-------------------------------------------------------------------------

    protected RouteType assertOneRoute(String uri) throws JAXBException {
        CamelContextType context = assertParseAsJaxb(uri);
        RouteType route = assertOneElement(context.getRoutes());
        return route;
    }

    protected void assertProcessor(RouteType route, String processorRef) {
        ProcessorType processor = assertOneElement(route.getProcessor());
        ProcessorRef to = assertIsInstanceOf(ProcessorRef.class, processor);
        assertEquals("Processor ref", processorRef, to.getRef());
    }

    protected void assertTo(RouteType route, String uri) {
        ProcessorType processor = assertOneElement(route.getProcessor());
        ToType value = assertIsInstanceOf(ToType.class, processor);
        assertEquals("To URI", uri, value.getUri());
    }

    protected void assertFrom(RouteType route, String uri) {
        FromType from = assertOneElement(route.getFrom());
        assertEquals("From URI", uri, from.getUri());
    }

    protected FilterType assertFilter(RouteType route) {
        ProcessorType processor = assertOneElement(route.getProcessor());
        return assertIsInstanceOf(FilterType.class, processor);
    }

    protected RecipientListType assertRecipientList(RouteType route) {
        ProcessorType processor = assertOneElement(route.getProcessor());
        return assertIsInstanceOf(RecipientListType.class, processor);
    }

    protected void assertExpression(ExpressionType expression, String language, String languageExpression) {
        assertNotNull("Expression should not be null!", expression);
        assertEquals("Expression language", language, expression.getLanguage());
        assertEquals("Expression", languageExpression, expression.getExpression());
    }

    protected void assertInterceptorRefs(RouteType route, String... names) {
        int idx = 0;
        List<InterceptorRef> interceptors = route.getInterceptors();
        for (String name : names) {
            int nextIdx = idx + 1;
            assertTrue("Not enough interceptors! Expected: " + nextIdx + " but have: " + interceptors, nextIdx <= interceptors.size());
            
            InterceptorRef interceptor = interceptors.get(idx++);
            assertEquals("Interceptor: " + idx, name, interceptor.getRef());
        }
    }
}
