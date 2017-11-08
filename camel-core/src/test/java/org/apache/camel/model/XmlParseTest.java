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

import java.util.List;
import javax.xml.bind.JAXBException;

import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.model.loadbalancer.CircuitBreakerLoadBalancerDefinition;
import org.apache.camel.model.loadbalancer.FailoverLoadBalancerDefinition;
import org.apache.camel.model.loadbalancer.RandomLoadBalancerDefinition;
import org.apache.camel.model.loadbalancer.RoundRobinLoadBalancerDefinition;
import org.apache.camel.model.loadbalancer.StickyLoadBalancerDefinition;
import org.apache.camel.model.loadbalancer.TopicLoadBalancerDefinition;

/**
 * @version 
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
        ProcessDefinition to = assertOneProcessorInstanceOf(ProcessDefinition.class, route);
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
        RecipientListDefinition<?> node = assertOneProcessorInstanceOf(RecipientListDefinition.class, route);
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

    public void testParseScriptXml() throws Exception {
        RouteDefinition route = assertOneRoute("script.xml");
        assertFrom(route, "direct:start");
        ScriptDefinition node = assertNthProcessorInstanceOf(ScriptDefinition.class, route, 0);
        assertExpression(node.getExpression(), "groovy", "System.out.println(\"groovy was here\")");
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

    @SuppressWarnings("deprecation")
    public void testParseSetOutHeaderXml() throws Exception {
        RouteDefinition route = assertOneRoute("setOutHeader.xml");
        assertFrom(route, "seda:a");
        SetOutHeaderDefinition node = assertNthProcessorInstanceOf(SetOutHeaderDefinition.class, route, 0);
        assertEquals("oldBodyValue", node.getHeaderName());
        assertExpression(node.getExpression(), "simple", "body");
        assertChildTo(route, "mock:b", 1);
    }

    @SuppressWarnings("deprecation")
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
    }

    public void testParseRoutingSlipXml() throws Exception {
        RouteDefinition route = assertOneRoute("routingSlip.xml");
        assertFrom(route, "seda:a");
        RoutingSlipDefinition<?> node = assertOneProcessorInstanceOf(RoutingSlipDefinition.class, route);
        assertEquals("destinations", node.getExpression().getExpression());
        assertEquals(RoutingSlipDefinition.DEFAULT_DELIMITER, node.getUriDelimiter());
    }

    public void testParseRoutingSlipWithHeaderSetXml() throws Exception {
        RouteDefinition route = assertOneRoute("routingSlipHeaderSet.xml");
        assertFrom(route, "seda:a");
        RoutingSlipDefinition<?> node = assertOneProcessorInstanceOf(RoutingSlipDefinition.class, route);
        assertEquals("theRoutingSlipHeader", node.getExpression().getExpression());
        assertEquals(RoutingSlipDefinition.DEFAULT_DELIMITER, node.getUriDelimiter());
    }

    public void testParseRoutingSlipWithHeaderAndDelimiterSetXml() throws Exception {
        RouteDefinition route = assertOneRoute("routingSlipHeaderAndDelimiterSet.xml");
        assertFrom(route, "seda:a");
        RoutingSlipDefinition<?> node = assertOneProcessorInstanceOf(RoutingSlipDefinition.class, route);
        assertEquals("theRoutingSlipHeader", node.getExpression().getExpression());
        assertEquals("#", node.getUriDelimiter());
    }

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

        SplitDefinition splitter = assertOneProcessorInstanceOf(SplitDefinition.class, route);
        assertExpression(splitter.getExpression(), "xpath", "/foo/bar");
        assertChildTo("to", splitter, "seda:b");
    }

    public void testParseLoadBalance() throws Exception {
        RouteDefinition route = assertOneRoute("routeWithLoadBalance.xml");
        assertFrom(route, "seda:a");
        LoadBalanceDefinition loadBalance = assertOneProcessorInstanceOf(LoadBalanceDefinition.class, route);
        assertEquals("Here should have 3 output here", 3, loadBalance.getOutputs().size());
        assertTrue("The loadBalancer should be RoundRobinLoadBalancerDefinition", loadBalance.getLoadBalancerType() instanceof RoundRobinLoadBalancerDefinition);
    }

    public void testParseCircuitBreakerLoadBalance() throws Exception {
        RouteDefinition route = assertOneRoute("routeWithCircuitBreakerLoadBalance.xml");
        assertFrom(route, "direct:start");
        LoadBalanceDefinition loadBalance = assertOneProcessorInstanceOf(LoadBalanceDefinition.class, route);
        assertEquals("Should have 1 output", 1, loadBalance.getOutputs().size());
        assertTrue("The loadBalancer should be CircuitBreakerLoadBalancerDefinition", loadBalance.getLoadBalancerType() instanceof CircuitBreakerLoadBalancerDefinition);
        CircuitBreakerLoadBalancerDefinition strategy = (CircuitBreakerLoadBalancerDefinition)loadBalance.getLoadBalancerType();
        assertEquals("Should have 1 exception", 1, strategy.getExceptions().size());
        assertEquals("Should have threshold of 2", 2, strategy.getThreshold().intValue());
        assertEquals("Should have HalfOpenAfter timeout of 1000L ", 1000L, strategy.getHalfOpenAfter().longValue());
    }

    public void testParseStickyLoadBalance() throws Exception {
        RouteDefinition route = assertOneRoute("routeWithStickyLoadBalance.xml");
        assertFrom(route, "seda:a");
        LoadBalanceDefinition loadBalance = assertOneProcessorInstanceOf(LoadBalanceDefinition.class, route);
        assertEquals("Here should have 3 output here", 3, loadBalance.getOutputs().size());
        assertTrue("The loadBalancer should be StickyLoadBalancerDefinition", loadBalance.getLoadBalancerType() instanceof StickyLoadBalancerDefinition);
        StickyLoadBalancerDefinition strategy = (StickyLoadBalancerDefinition)loadBalance.getLoadBalancerType();
        assertNotNull("the expression should not be null ", strategy.getCorrelationExpression());
    }

    public void testParseFailoverLoadBalance() throws Exception {
        RouteDefinition route = assertOneRoute("routeWithFailoverLoadBalance.xml");
        assertFrom(route, "seda:a");
        LoadBalanceDefinition loadBalance = assertOneProcessorInstanceOf(LoadBalanceDefinition.class, route);
        assertEquals("Here should have 3 output here", 3, loadBalance.getOutputs().size());
        assertTrue("The loadBalancer should be FailoverLoadBalancerDefinition", loadBalance.getLoadBalancerType() instanceof FailoverLoadBalancerDefinition);
        FailoverLoadBalancerDefinition strategy = (FailoverLoadBalancerDefinition)loadBalance.getLoadBalancerType();
        assertEquals("there should be 2 exceptions", 2, strategy.getExceptions().size());
    }

    public void testParseRandomLoadBalance() throws Exception {
        RouteDefinition route = assertOneRoute("routeWithRandomLoadBalance.xml");
        assertFrom(route, "seda:a");
        LoadBalanceDefinition loadBalance = assertOneProcessorInstanceOf(LoadBalanceDefinition.class, route);
        assertEquals("Here should have 3 output here", 3, loadBalance.getOutputs().size());
        assertTrue("The loadBalancer should be RandomLoadBalancerDefinition", loadBalance.getLoadBalancerType() instanceof RandomLoadBalancerDefinition);
    }

    public void testParseTopicLoadBalance() throws Exception {
        RouteDefinition route = assertOneRoute("routeWithTopicLoadBalance.xml");
        assertFrom(route, "seda:a");
        LoadBalanceDefinition loadBalance = assertOneProcessorInstanceOf(LoadBalanceDefinition.class, route);
        assertEquals("Here should have 3 output here", 3, loadBalance.getOutputs().size());
        assertTrue("The loadBalancer should be TopicLoadBalancerDefinition", loadBalance.getLoadBalancerType() instanceof TopicLoadBalancerDefinition);
    }

    public void testParseHL7DataFormat() throws Exception {
        RouteDefinition route = assertOneRoute("routeWithHL7DataFormat.xml");
        assertFrom(route, "seda:a");
    }

    public void testParseXStreamDataFormat() throws Exception {
        RouteDefinition route = assertOneRoute("routeWithXStreamDataFormat.xml");
        assertFrom(route, "seda:a");
    }

    public void testParseJibxDataFormat() throws Exception {
        RouteDefinition route = assertOneRoute("routeWithJibxDataFormat.xml");
        assertFrom(route, "seda:a");
    }

    public void testParseXMLBeansDataFormat() throws Exception {
        RouteDefinition route = assertOneRoute("routeWithXMLBeansDataFormat.xml");
        assertFrom(route, "seda:a");
    }

    public void testParseXMLSecurityDataFormat() throws Exception {
        RouteDefinition route = assertOneRoute("routeWithXMLSecurityDataFormat.xml");
        assertFrom(route, "seda:a");
    }

    public void testParseTidyMarkupDataFormat() throws Exception {
        RouteDefinition route = assertOneRoute("routeWithTidyMarkupDataFormat.xml");
        assertFrom(route, "seda:a");
    }

    public void testParseRSSDataFormat() throws Exception {
        RouteDefinition route = assertOneRoute("routeWithRSSDataFormat.xml");
        assertFrom(route, "seda:a");
    }

    public void testParseJSonDataFormat() throws Exception {
        RouteDefinition route = assertOneRoute("routeWithJSonDataFormat.xml");
        assertFrom(route, "seda:a");
    }

    public void testParseJaxbDataFormat() throws Exception {
        RouteDefinition route = assertOneRoute("routeWithJaxbDataFormat.xml");
        assertFrom(route, "seda:a");
    }

    public void testParseFlatpackDataFormat() throws Exception {
        RouteDefinition route = assertOneRoute("routeWithFlatpackDataFormat.xml");
        assertFrom(route, "seda:a");
    }

    public void testParseCvsDataFormat() throws Exception {
        RouteDefinition route = assertOneRoute("routeWithCvsDataFormat.xml");
        assertFrom(route, "seda:a");
    }

    public void testParseZipFileDataFormat() throws Exception {
        RouteDefinition route = assertOneRoute("routeWithZipFileDataFormat.xml");
        assertFrom(route, "seda:a");
    }

    public void testParseBindyDataFormat() throws Exception {
        RouteDefinition route = assertOneRoute("routeWithBindyDataFormat.xml");
        assertFrom(route, "seda:a");
    }

    public void testParseCastorDataFormat() throws Exception {
        RouteDefinition route = assertOneRoute("routeWithCastorDataFormat.xml");
        assertFrom(route, "seda:a");
    }

    public void testParseBatchResequencerXml() throws Exception {
        RouteDefinition route = assertOneRoute("resequencerBatch.xml");
        ResequenceDefinition resequencer = assertOneProcessorInstanceOf(ResequenceDefinition.class, route);
        assertNull(resequencer.getStreamConfig());
        assertNotNull(resequencer.getBatchConfig());
        assertEquals(500, resequencer.getBatchConfig().getBatchSize());
        assertEquals(2000L, resequencer.getBatchConfig().getBatchTimeout());
    }

    public void testParseStreamResequencerXml() throws Exception {
        RouteDefinition route = assertOneRoute("resequencerStream.xml");
        ResequenceDefinition resequencer = assertOneProcessorInstanceOf(ResequenceDefinition.class, route);
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
        log.info("Testing: {} is equal to: {} for processor: {}", text, uri, processor);
        assertEquals(text, uri, value.getUri());
    }

    protected void assertTo(String message, ProcessorDefinition<?> processor, String uri) {
        ToDefinition value = assertIsInstanceOf(ToDefinition.class, processor);
        String text = message + "To URI";
        log.info("Testing: {} is equal to: {} for processor: {}", text, uri, processor);
        assertEquals(text, uri, value.getUri());
    }

    protected void assertChildTo(ProcessorDefinition<?> route, String... uris) {
        List<ProcessorDefinition<?>> list = assertListSize(route.getOutputs(), uris.length);
        int idx = 0;
        for (String uri : uris) {
            assertTo("output[" + idx + "] ", list.get(idx++), uri);
        }
    }

    protected void assertChildTo(ProcessorDefinition<?> route, String uri, int toIdx) {
        List<ProcessorDefinition<?>> list = route.getOutputs();
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
}
