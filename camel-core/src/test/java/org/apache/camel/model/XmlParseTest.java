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
package org.apache.camel.model;

import org.apache.camel.model.language.ExpressionType;

import javax.xml.bind.JAXBException;
import java.util.List;

/**
 * @version $Revision: 1.1 $
 */
public class XmlParseTest extends XmlTestSupport {
    public void testParseSimpleRouteXml() throws Exception {
        RouteType route = assertOneRoute("simpleRoute.xml");
        assertFrom(route, "seda:a");
        assertChildTo("to", route, "seda:b");
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

    public void testParseStaticRecipientListXml() throws Exception {
        RouteType route = assertOneRoute("staticRecipientList.xml");
        assertFrom(route, "seda:a");
        assertChildTo(route, "seda:b", "seda:c", "seda:d");
    }

    public void testParseRouteWithInterceptorXml() throws Exception {
        RouteType route = assertOneRoute("routeWithInterceptor.xml");
        assertFrom(route, "seda:a");
        assertChildTo("to", route, "seda:d");
        assertInterceptorRefs(route, "interceptor1", "interceptor2");
    }

    public void testParseRouteWithChoiceXml() throws Exception {
        RouteType route = assertOneRoute("routeWithChoice.xml");
        assertFrom(route, "seda:a");

        ChoiceType choice = assertChoice(route);
        List<WhenType> whens = assertListSize(choice.getWhenClauses(), 2);
        assertChildTo("when(0)", whens.get(0), "seda:b");
        assertChildTo("when(1)", whens.get(1), "seda:c");

        OtherwiseType otherwise = choice.getOtherwise();
        assertNotNull("Otherwise is null", otherwise);
        assertChildTo("otherwise", otherwise, "seda:d");
    }

    public void testParseSplitterXml() throws Exception {
        RouteType route = assertOneRoute("splitter.xml");
        assertFrom(route, "seda:a");

        SplitterType splitter = assertSplitter(route);
        assertExpression(splitter.getExpression(), "xpath", "/foo/bar");
        assertChildTo("to", splitter, "seda:b");
    }

    public void testParseBatchResequencerXml() throws Exception {
        RouteType route = assertOneRoute("resequencerBatch.xml");
        ResequencerType resequencer = assertResequencer(route);
        assertNull(resequencer.getStreamConfig());
        assertNotNull(resequencer.getBatchConfig());
        assertEquals(500, resequencer.getBatchConfig().getBatchSize());
        assertEquals(2000L, resequencer.getBatchConfig().getBatchTimeout());
    }
    
    public void testParseStreamResequencerXml() throws Exception {
        RouteType route = assertOneRoute("resequencerStream.xml");
        ResequencerType resequencer = assertResequencer(route);
        assertNotNull(resequencer.getStreamConfig());
        assertNull(resequencer.getBatchConfig());
        assertEquals(100, resequencer.getStreamConfig().getCapacity());
        assertEquals(2000L, resequencer.getStreamConfig().getTimeout());
    }
    
    // Implementation methods
    // -------------------------------------------------------------------------

    protected RouteType assertOneRoute(String uri) throws JAXBException {
        RouteContainer context = assertParseAsJaxb(uri);
        RouteType route = assertOneElement(context.getRoutes());
        return route;
    }

    protected void assertFrom(RouteType route, String uri) {
        FromType from = assertOneElement(route.getInputs());
        assertEquals("From URI", uri, from.getUri());
    }

    protected void assertChildTo(String message, ProcessorType<?> route, String uri) {
        ProcessorType<?> processor = assertOneElement(route.getOutputs());
        ToType value = assertIsInstanceOf(ToType.class, processor);
        String text = message + "To URI";
        log.info("Testing: " + text + " is equal to: " + uri + " for processor: " + processor);
        assertEquals(text, uri, value.getUri());
    }

    protected void assertTo(String message, ProcessorType processor, String uri) {
        ToType value = assertIsInstanceOf(ToType.class, processor);
        String text = message + "To URI";
        log.info("Testing: " + text + " is equal to: " + uri + " for processor: " + processor);
        assertEquals(text, uri, value.getUri());
    }

    protected void assertChildTo(ProcessorType route, String... uris) {
        List<ProcessorType<?>> list = assertListSize(route.getOutputs(), uris.length);
        int idx = 0;
        for (String uri : uris) {
            assertTo("output[" + idx + "] ", list.get(idx++), uri);
        }
    }

    protected void assertProcessor(ProcessorType<?> route, String processorRef) {
        ProcessorType<?> processor = assertOneElement(route.getOutputs());
        ProcessorRef to = assertIsInstanceOf(ProcessorRef.class, processor);
        assertEquals("Processor ref", processorRef, to.getRef());
    }

    protected FilterType assertFilter(ProcessorType<?> route) {
        ProcessorType<?> processor = assertOneElement(route.getOutputs());
        return assertIsInstanceOf(FilterType.class, processor);
    }

    protected RecipientListType assertRecipientList(ProcessorType<?> route) {
        ProcessorType<?> processor = assertOneElement(route.getOutputs());
        return assertIsInstanceOf(RecipientListType.class, processor);
    }

    protected ChoiceType assertChoice(ProcessorType<?> route) {
        ProcessorType<?> processor = assertOneElement(route.getOutputs());
        return assertIsInstanceOf(ChoiceType.class, processor);
    }

    protected SplitterType assertSplitter(ProcessorType<?> route) {
        ProcessorType<?> processor = assertOneElement(route.getOutputs());
        return assertIsInstanceOf(SplitterType.class, processor);
    }

    protected ResequencerType assertResequencer(ProcessorType<?> route) {
        ProcessorType<?> processor = assertOneElement(route.getOutputs());
        return assertIsInstanceOf(ResequencerType.class, processor);
    }

    protected void assertExpression(ExpressionType expression, String language, String languageExpression) {
        assertNotNull("Expression should not be null!", expression);
        assertEquals("Expression language", language, expression.getLanguage());
        assertEquals("Expression", languageExpression, expression.getExpression());
    }

    protected void assertInterceptorRefs(ProcessorType route, String... names) {
        int idx = 0;
        List<InterceptorType> interceptors = route.getInterceptors();
        for (String name : names) {
            int nextIdx = idx + 1;
            assertTrue("Not enough interceptors! Expected: " + nextIdx + " but have: " + interceptors,
                    nextIdx <= interceptors.size());

            InterceptorRef interceptor = assertIsInstanceOf(InterceptorRef.class, interceptors.get(idx++));
            assertEquals("Interceptor: " + idx, name, interceptor.getRef());
        }
    }
}
