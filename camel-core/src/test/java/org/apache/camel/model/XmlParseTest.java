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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.bind.JAXBException;

import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.model.loadbalancer.RoundRobinLoadBalanceStrategy;
import org.apache.camel.model.loadbalancer.StickyLoadBalanceStrategy;

/**
 * @version $Revision$
 */
public class XmlParseTest extends XmlTestSupport {
    public void testParseSimpleRouteXml() throws Exception {
        RouteDefinition route = assertOneRoute("simpleRoute.xml");
        assertFrom(route, "seda:a");
        assertChildTo("to", route, "seda:b");
    }

    public void testParseProcessorXml() throws Exception {
        RouteDefinition route = assertOneRoute("processor.xml");
        assertFrom(route, "seda:a");
        ProcessorRef to = assertOneProcessorInstanceOf(ProcessorRef.class, route);
        assertEquals("Processor ref", "myProcessor", to.getRef());
    }

    public void testParseProcessorWithFilterXml() throws Exception {
        RouteDefinition route = assertOneRoute("processorWithFilter.xml");
        assertFrom(route, "seda:a");
        FilterDefinition filter = assertOneProcessorInstanceOf(FilterDefinition.class, route);
        assertExpression(filter.getExpression(), "juel", "in.header.foo == 'bar'");
    }

    public void testParseProcessorWithHeaderFilterXml() throws Exception {
        RouteDefinition route = assertOneRoute("processorWithHeaderFilter.xml");
        assertFrom(route, "seda:a");
        FilterDefinition filter = assertOneProcessorInstanceOf(FilterDefinition.class, route);
        assertExpression(filter.getExpression(), "header", "foo");
    }

    public void testParseProcessorWithElFilterXml() throws Exception {
        RouteDefinition route = assertOneRoute("processorWithElFilter.xml");
        assertFrom(route, "seda:a");
        FilterDefinition filter = assertOneProcessorInstanceOf(FilterDefinition.class, route);
        assertExpression(filter.getExpression(), "el", "$in.header.foo == 'bar'");
    }

    public void testParseProcessorWithGroovyFilterXml() throws Exception {
        RouteDefinition route = assertOneRoute("processorWithGroovyFilter.xml");
        assertFrom(route, "seda:a");
        FilterDefinition filter = assertOneProcessorInstanceOf(FilterDefinition.class, route);
        assertExpression(filter.getExpression(), "groovy", "in.headers.any { h -> h.startsWith('foo')}");
    }

    public void testParseRecipientListXml() throws Exception {
        RouteDefinition route = assertOneRoute("dynamicRecipientList.xml");
        assertFrom(route, "seda:a");
        RecipientListDefinition node = assertOneProcessorInstanceOf(RecipientListDefinition.class, route);
        assertExpression(node.getExpression(), "header", "foo");
    }

    public void testParseStaticRecipientListXml() throws Exception {
        RouteDefinition route = assertOneRoute("staticRecipientList.xml");
        assertFrom(route, "seda:a");
        assertChildTo(route, "seda:b", "seda:c", "seda:d");
    }

    public void testParseTransformXml() throws Exception {
        RouteDefinition route = assertOneRoute("transform.xml");
        assertFrom(route, "direct:start");
        TransformDefinition node = assertNthProcessorInstanceOf(TransformDefinition.class, route, 0);
        assertExpression(node.getExpression(), "simple", "${in.body} extra data!");
        assertChildTo(route, "mock:end", 1);
    }

    public void testParseSetBodyXml() throws Exception {
        RouteDefinition route = assertOneRoute("setBody.xml");
        assertFrom(route, "direct:start");
        SetBodyDefinition node = assertNthProcessorInstanceOf(SetBodyDefinition.class, route, 0);
        assertExpression(node.getExpression(), "simple", "${in.body} extra data!");
        assertChildTo(route, "mock:end", 1);
    }

    public void testParseSetHeaderXml() throws Exception {
        RouteDefinition route = assertOneRoute("setHeader.xml");
        assertFrom(route, "seda:a");
        SetHeaderDefinition node = assertNthProcessorInstanceOf(SetHeaderDefinition.class, route, 0);
        assertEquals("oldBodyValue", node.getHeaderName());
        assertExpression(node.getExpression(), "simple", "body");
        assertChildTo(route, "mock:b", 1);
    }

    public void testParseSetHeaderToConstantXml() throws Exception {
        RouteDefinition route = assertOneRoute("setHeaderToConstant.xml");
        assertFrom(route, "seda:a");
        SetHeaderDefinition node = assertNthProcessorInstanceOf(SetHeaderDefinition.class, route, 0);
        assertEquals("theHeader", node.getHeaderName());
        assertExpression(node.getExpression(), "constant", "a value");
        assertChildTo(route, "mock:b", 1);
    }

    public void testParseSetOutHeaderXml() throws Exception {
        RouteDefinition route = assertOneRoute("setOutHeader.xml");
        assertFrom(route, "seda:a");
        SetOutHeaderDefinition node = assertNthProcessorInstanceOf(SetOutHeaderDefinition.class, route, 0);
        assertEquals("oldBodyValue", node.getHeaderName());
        assertExpression(node.getExpression(), "simple", "body");
        assertChildTo(route, "mock:b", 1);
    }

    public void testParseSetOutHeaderToConstantXml() throws Exception {
        RouteDefinition route = assertOneRoute("setOutHeaderToConstant.xml");
        assertFrom(route, "seda:a");
        SetOutHeaderDefinition node = assertNthProcessorInstanceOf(SetOutHeaderDefinition.class, route, 0);
        assertEquals("theHeader", node.getHeaderName());
        assertExpression(node.getExpression(), "constant", "a value");
        assertChildTo(route, "mock:b", 1);
    }

    public void testParseConvertBodyXml() throws Exception {
        RouteDefinition route = assertOneRoute("convertBody.xml");
        assertFrom(route, "seda:a");
        ConvertBodyDefinition node = assertOneProcessorInstanceOf(ConvertBodyDefinition.class, route);
        assertEquals("java.lang.Integer", node.getType());
        assertEquals(Integer.class, node.getTypeClass());
    }

    public void testParseRoutingSlipXml() throws Exception {
        RouteDefinition route = assertOneRoute("routingSlip.xml");
        assertFrom(route, "seda:a");
        RoutingSlipDefinition node = assertOneProcessorInstanceOf(RoutingSlipDefinition.class, route);
        assertEquals("destinations", node.getHeaderName());
        assertEquals(RoutingSlipDefinition.DEFAULT_DELIMITER, node.getUriDelimiter());
    }

    public void testParseRoutingSlipWithHeaderSetXml() throws Exception {
        RouteDefinition route = assertOneRoute("routingSlipHeaderSet.xml");
        assertFrom(route, "seda:a");
        RoutingSlipDefinition node = assertOneProcessorInstanceOf(RoutingSlipDefinition.class, route);
        assertEquals("theRoutingSlipHeader", node.getHeaderName());
        assertEquals(RoutingSlipDefinition.DEFAULT_DELIMITER, node.getUriDelimiter());
    }

    public void testParseRoutingSlipWithHeaderAndDelimiterSetXml() throws Exception {
        RouteDefinition route = assertOneRoute("routingSlipHeaderAndDelimiterSet.xml");
        assertFrom(route, "seda:a");
        RoutingSlipDefinition node = assertOneProcessorInstanceOf(RoutingSlipDefinition.class, route);
        assertEquals("theRoutingSlipHeader", node.getHeaderName());
        assertEquals("#", node.getUriDelimiter());
    }

    //TODO get the test fixed
    public void xtestParseRouteWithInterceptorXml() throws Exception {
        RouteDefinition route = assertOneRoute("routeWithInterceptor.xml");
        assertFrom(route, "seda:a");
        assertChildTo("to", route, "seda:d");
        assertInterceptorRefs(route, "interceptor1", "interceptor2");
    }

    @SuppressWarnings("unchecked")
    public void testParseRouteWithChoiceXml() throws Exception {
        RouteDefinition route = assertOneRoute("routeWithChoice.xml");
        assertFrom(route, "seda:a");

        ChoiceDefinition choice = assertOneProcessorInstanceOf(ChoiceDefinition.class, route);
        List<WhenDefinition> whens = assertListSize(choice.getWhenClauses(), 2);
        assertChildTo("when(0)", whens.get(0), "seda:b");
        assertChildTo("when(1)", whens.get(1), "seda:c");

        OtherwiseDefinition otherwise = choice.getOtherwise();
        assertNotNull("Otherwise is null", otherwise);
        assertChildTo("otherwise", otherwise, "seda:d");
    }

    public void testParseSplitterXml() throws Exception {
        RouteDefinition route = assertOneRoute("splitter.xml");
        assertFrom(route, "seda:a");

        SplitterDefinition splitter = assertOneProcessorInstanceOf(SplitterDefinition.class, route);
        assertExpression(splitter.getExpression(), "xpath", "/foo/bar");
        assertChildTo("to", splitter, "seda:b");
    }

    public void testParseLoadBalance() throws Exception {
        RouteDefinition route = assertOneRoute("routeWithLoadBalance.xml");
        assertFrom(route, "seda:a");
        LoadBalanceDefinition loadBalance = assertOneProcessorInstanceOf(LoadBalanceDefinition.class, route);
        assertEquals("Here should have 3 output here", 3, loadBalance.getOutputs().size());
        assertTrue("The loadBalancer shoud be RoundRobinLoadBalanceStrategy", loadBalance.getLoadBalancerType() instanceof RoundRobinLoadBalanceStrategy);
    }

    public void testParseStickyLoadBalance() throws Exception {
        RouteDefinition route = assertOneRoute("routeWithStickyLoadBalance.xml");
        assertFrom(route, "seda:a");
        LoadBalanceDefinition loadBalance = assertOneProcessorInstanceOf(LoadBalanceDefinition.class, route);
        assertEquals("Here should have 3 output here", 3, loadBalance.getOutputs().size());
        assertTrue("The loadBalancer shoud be StickyLoadBalanceStrategy", loadBalance.getLoadBalancerType() instanceof StickyLoadBalanceStrategy);
        StickyLoadBalanceStrategy strategy = (StickyLoadBalanceStrategy)loadBalance.getLoadBalancerType();
        assertNotNull("the expression should not be null ", strategy.getExpressionType());
    }

    public void testParseBatchResequencerXml() throws Exception {
        RouteDefinition route = assertOneRoute("resequencerBatch.xml");
        ResequencerDefinition resequencer = assertOneProcessorInstanceOf(ResequencerDefinition.class, route);
        assertNull(resequencer.getStreamConfig());
        assertNotNull(resequencer.getBatchConfig());
        assertEquals(500, resequencer.getBatchConfig().getBatchSize());
        assertEquals(2000L, resequencer.getBatchConfig().getBatchTimeout());
    }

    public void testParseStreamResequencerXml() throws Exception {
        RouteDefinition route = assertOneRoute("resequencerStream.xml");
        ResequencerDefinition resequencer = assertOneProcessorInstanceOf(ResequencerDefinition.class, route);
        assertNotNull(resequencer.getStreamConfig());
        assertNull(resequencer.getBatchConfig());
        assertEquals(1000, resequencer.getStreamConfig().getCapacity());
        assertEquals(2000L, resequencer.getStreamConfig().getTimeout());
    }

    public void testLoop() throws Exception {
        RouteDefinition route = assertOneRoute("loop.xml");
        LoopDefinition loop = assertOneProcessorInstanceOf(LoopDefinition.class, route);
        assertNotNull(loop.getExpression());
        assertEquals("constant", loop.getExpression().getLanguage());
    }

    // Implementation methods
    // -------------------------------------------------------------------------

    protected RouteDefinition assertOneRoute(String uri) throws JAXBException {
        RouteContainer context = assertParseAsJaxb(uri);
        RouteDefinition route = assertOneElement(context.getRoutes());
        return route;
    }

    protected void assertFrom(RouteDefinition route, String uri) {
        FromDefinition from = assertOneElement(route.getInputs());
        assertEquals("From URI", uri, from.getUri());
    }

    protected void assertChildTo(String message, ProcessorDefinition<?> route, String uri) {
        ProcessorDefinition<?> processor = assertOneElement(route.getOutputs());
        ToDefinition value = assertIsInstanceOf(ToDefinition.class, processor);
        String text = message + "To URI";
        log.info("Testing: " + text + " is equal to: " + uri + " for processor: " + processor);
        assertEquals(text, uri, value.getUri());
    }

    protected void assertTo(String message, ProcessorDefinition<?> processor, String uri) {
        ToDefinition value = assertIsInstanceOf(ToDefinition.class, processor);
        String text = message + "To URI";
        log.info("Testing: " + text + " is equal to: " + uri + " for processor: " + processor);
        assertEquals(text, uri, value.getUri());
    }

    protected void assertChildTo(ProcessorDefinition<?> route, String... uris) {
        List<ProcessorDefinition> list = assertListSize(route.getOutputs(), uris.length);
        int idx = 0;
        for (String uri : uris) {
            assertTo("output[" + idx + "] ", list.get(idx++), uri);
        }
    }

    protected void assertChildTo(ProcessorDefinition<?> route, String uri, int toIdx) {
        List<ProcessorDefinition> list = route.getOutputs();
        assertTo("to and idx=" + toIdx, list.get(toIdx), uri);
    }

    protected <T> T assertOneProcessorInstanceOf(Class<T> type, ProcessorDefinition<?> route) {
        ProcessorDefinition<?> processor = assertOneElement(route.getOutputs());
        return assertIsInstanceOf(type, processor);
    }

    protected <T> T assertNthProcessorInstanceOf(Class<T> type, ProcessorDefinition<?> route, int index) {
        ProcessorDefinition<?> processor = route.getOutputs().get(index);
        return assertIsInstanceOf(type, processor);
    }

    protected void assertExpression(ExpressionDefinition expression, String language, String languageExpression) {
        assertNotNull("Expression should not be null!", expression);
        assertEquals("Expression language", language, expression.getLanguage());
        assertEquals("Expression", languageExpression, expression.getExpression());
    }

    protected void assertInterceptorRefs(ProcessorDefinition route, String... names) {
        RouteDefinition rt = (RouteDefinition)route;
        assertNotNull(rt);

        // Rely on the fact that reference ids are unique
        List<InterceptorDefinition> interceptors = rt.getInterceptors();
        assertEquals("Interceptor count does not match", names.length, interceptors.size());

        Set<String> refs = new HashSet<String>();
        for (InterceptorDefinition it : interceptors) {
            InterceptorRef ir = assertIsInstanceOf(InterceptorRef.class, it);
            refs.add(ir.getRef());
        }
        for (String name : names) {
            assertTrue("Interceptor \"" + name + "\" not found", refs.contains(name));
        }
    }
}
