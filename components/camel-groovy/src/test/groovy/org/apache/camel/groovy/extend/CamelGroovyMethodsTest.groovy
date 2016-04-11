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
package org.apache.camel.groovy.extend

import org.apache.camel.EndpointInject
import org.apache.camel.Exchange
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.component.mock.MockEndpoint
import org.apache.camel.test.junit4.CamelTestSupport
import org.junit.Test

/**
 * Test a few DSL extensions. 
 */
class CamelGroovyMethodsTest extends CamelTestSupport {

    private static final String HELLO = 'Hello'
    private static final String WORLD = 'World'
    
    @EndpointInject(uri = 'mock:test1')
    private MockEndpoint resultEndpoint;
    @EndpointInject(uri = 'mock:test2')
    private MockEndpoint otherEndpoint;
    
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        
        def aggregation = { Exchange original, Exchange resource ->
                        original.in.body += resource.in.body
                        original
                    }
        
        return new RouteBuilder() {

            @Override
            public void configure() throws Exception {
                
                from('direct:test1')
                    .enrich('direct:enrich') { Exchange original, Exchange resource -> 
                        original.in.body += resource.in.body
                        original
                    }
                    .to('mock:test1')
                
                from('direct:enrich')
                    .transform(constant(WORLD))
                    
                from('direct:test2')
                    .pollEnrich('seda:enrich', aggregation)
                    .to('mock:test1')
                    
                from('direct:test3')
                    .process { Exchange e ->
                        e.in.with {
                            body = HELLO
                            headers[HELLO] = WORLD
                        }
                    }
                    .to('mock:test1')
                    
                from('direct:test4')
                    .setHeader(HELLO) { Exchange e ->
                        e.in.body.reverse()
                    }
                    .to('mock:test1')
                    
                from('direct:test5')
                    .setProperty(HELLO) { Exchange e ->
                        e.in.body.reverse()
                    }
                    .to('mock:test1')
                    
                from('direct:test6')
                    .transform { Exchange e ->
                        e.in.body.reverse()
                    }
                    .to('mock:test1')
                    
                from('direct:test7')
                    .setBody { Exchange e ->
                        e.in.body.reverse()
                    }
                    .to('mock:test1')
                    
                from('direct:test8')
                    .unmarshal().gpath()
                    // XmlSlurper proceeds to first node
                    .transform { it.in.body.World.text() }
                    .to('mock:test1')
                    
                from('direct:test9')
                    .unmarshal().gnode()
                    // XmlParser proceeds to first node
                    .transform { it.in.body.World.text() }
                    .to('mock:test1')
                    
                from('direct:test10')
                    .marshal().gnode()
                    .convertBodyTo(String)
                    .to('mock:test1')
                    
                from('direct:test11')
                    .choice()
                        .when { it.in.body == HELLO }.to('mock:test1')
                        .otherwise().to('mock:test2')

                from('direct:test12')
                    .setHeader(HELLO).expression { Exchange e ->
                        e.in.body.reverse()
                    }
                    .to('mock:test1')

                from('direct:toD')
                    .toD('mock:${header.foo}')

            }
            
        }
    }

    @Test
    void testClosureEnricherAggregation() {
        resultEndpoint.expectedBodiesReceived(HELLO + WORLD)
        template.sendBody('direct:test1', HELLO)
        resultEndpoint.assertIsSatisfied()
    }
    
    @Test
    void testClosurePollingEnricherAggregation() {
        resultEndpoint.expectedBodiesReceived(HELLO + WORLD)
        template.sendBody('seda:enrich', WORLD)
        template.sendBody('direct:test2', HELLO)
        resultEndpoint.assertIsSatisfied()
    }
    
    @Test
    void testClosureProcessor() {
        resultEndpoint.expectedBodiesReceived(HELLO)
        resultEndpoint.expectedHeaderReceived(HELLO, WORLD)
        template.sendBody('direct:test3', '')
        resultEndpoint.assertIsSatisfied()
    }
    
    @Test
    void testClosureSetHeader() {
        resultEndpoint.expectedHeaderReceived(HELLO, WORLD)
        template.sendBody('direct:test4', WORLD.reverse())
        resultEndpoint.assertIsSatisfied()
    }

    @Test
    void testClosureSetHeaderWithExpressionClause() {
        resultEndpoint.expectedHeaderReceived(HELLO, WORLD)
        template.sendBody('direct:test4', WORLD.reverse())
        resultEndpoint.assertIsSatisfied()
    }
    
    @Test
    void testClosureSetProperty() {
        resultEndpoint.expectedPropertyReceived(HELLO, WORLD)
        template.sendBody('direct:test5', WORLD.reverse())
        resultEndpoint.assertIsSatisfied()
    }

    @Test
    void testClosureTransformer() {
        resultEndpoint.expectedBodiesReceived(HELLO)
        template.sendBody('direct:test6', HELLO.reverse())
        resultEndpoint.assertIsSatisfied()
    }
    
    @Test
    void testClosureSetBody() {
        resultEndpoint.expectedBodiesReceived(HELLO)
        template.sendBody('direct:test7', HELLO.reverse())
        resultEndpoint.assertIsSatisfied()
    }
    
    @Test
    void testClosureChoice1() {
        resultEndpoint.expectedBodiesReceived(HELLO)
        otherEndpoint.expectedMessageCount(0)
        template.sendBody('direct:test11', HELLO)
        resultEndpoint.assertIsSatisfied()
        otherEndpoint.assertIsSatisfied()
    }
    
    @Test
    void testClosureChoice2() {
        resultEndpoint.expectedMessageCount(0)
        otherEndpoint.expectedBodiesReceived(WORLD)
        template.sendBody('direct:test11', WORLD)
        resultEndpoint.assertIsSatisfied()
        otherEndpoint.assertIsSatisfied()
    }
    
    @Test
    void testXmlSlurper() {
        String text = "How are you?"
        resultEndpoint.expectedBodiesReceived(text)
        template.sendBody('direct:test8', "<Hello><World>${text}</World></Hello>")
        resultEndpoint.assertIsSatisfied()
    }
    
    @Test
    void testXmlParser() {
        String text = "How are you?"
        resultEndpoint.expectedBodiesReceived(text)
        template.sendBody('direct:test9', "<Hello><World>${text}</World></Hello>")
        resultEndpoint.assertIsSatisfied()
    }
    
    @Test
    void testXmlPrinter() {
        String text = "<Hello><World>How are you?</World></Hello>"
        Node parsed = new XmlParser().parseText(text)
        resultEndpoint.expectedMessageCount(1)
        template.sendBody('direct:test10', parsed)
        // The created XML differs in terms of white spaces and line feeds.
        assertEquals(text.replaceAll('\\s+', ''), resultEndpoint.exchanges[0].in.body.replaceAll('\\s+', ''))
    }

    @Test
    void testToD() {
        resultEndpoint.expectedMessageCount(1)
        template.sendBodyAndHeader('direct:toD', WORLD, "foo", "test1")
        resultEndpoint.assertIsSatisfied()
    }

}
