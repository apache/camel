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
package org.apache.camel.builder.script;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * Tests a routing expression using JavaScript
 */
public class JavaScriptExpressionTest extends CamelTestSupport {

    @Test
    public void testSendMatchingMessage() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        getMockEndpoint("mock:unmatched").expectedMessageCount(0);

        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put("foo", "bar");
        sendBody("direct:start", "hello", headers);

        assertEquals("Should get the message header here", mock.getExchanges().get(0).getIn().getHeader("foo"), "bar");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSendNonMatchingMessage() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(0);
        getMockEndpoint("mock:unmatched").expectedMessageCount(1);

        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put("foo", "foo");
        sendBody("direct:start", "hello", headers);

        assertMockEndpointsSatisfied();
    }

    @Test
    // START SNIPPET: e1
    public void testArgumentsExample() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(0);
        getMockEndpoint("mock:unmatched").expectedMessageCount(1);

        // additional arguments to ScriptEngine
        Map<String, Object> arguments = new HashMap<String, Object>();
        arguments.put("foo", "bar");
        arguments.put("baz", 7);

        // those additional arguments is provided as a header on the Camel Message
        template.sendBodyAndHeader("direct:start", "hello", ScriptBuilder.ARGUMENTS, arguments);

        assertMockEndpointsSatisfied();
    }
    // END SNIPPET: e1

    @Test
    public void testArgumentsWithStringMap() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(0);
        getMockEndpoint("mock:unmatched").expectedMessageCount(1);

        Map<String, Object> headers = new HashMap<String, Object>();
        Map<String, Object> arguments = new HashMap<String, Object>();
        arguments.put("foo", "bar");
        arguments.put("baz", 7);
        arguments.put("", "foo");
        arguments.put(null, "bar");
        headers.put(ScriptBuilder.ARGUMENTS, arguments);

        sendBody("direct:start", "hello", headers);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testArgumentsWithIntegerMap() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(0);
        getMockEndpoint("mock:unmatched").expectedMessageCount(1);

        Map<String, Object> headers = new HashMap<String, Object>();
        Map<Integer, Object> arguments = new HashMap<Integer, Object>();
        arguments.put(0, "bar");
        arguments.put(1, 7);
        headers.put(ScriptBuilder.ARGUMENTS, arguments);

        sendBody("direct:start", "hello", headers);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testArgumentsWithNonMap() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(0);
        getMockEndpoint("mock:unmatched").expectedMessageCount(1);

        Map<String, Object> headers = new HashMap<String, Object>();
        String arguments = "foo";
        headers.put(ScriptBuilder.ARGUMENTS, arguments);

        sendBody("direct:start", "hello", headers);

        assertMockEndpointsSatisfied();
    }
    
    @Test
    public void testSendingRequestInMutipleThreads() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(0);
        getMockEndpoint("mock:unmatched").expectedMessageCount(100);

        long start = System.currentTimeMillis();
        ExecutorService executorService = Executors.newFixedThreadPool(100);
        for (int i = 0; i < 100; i++) {
            executorService.submit(new Runnable() {
                @Override
                public void run() {
                    Map<String, Object> headers = new HashMap<String, Object>();
                    String arguments = "foo";
                    headers.put(ScriptBuilder.ARGUMENTS, arguments);
    
                    sendBody("direct:start", "hello", headers);
                    
                }
                
            });
        }
            
        assertMockEndpointsSatisfied();
        long delta = System.currentTimeMillis() - start;
        log.info("Processing the 100 request tooks: " + delta + " ms");

        executorService.shutdown();
    }
    
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:start").choice().
                        when().javaScript("request.headers.get('foo') == 'bar'").to("log:info?showAll=true").to("mock:result")
                        .otherwise().to("mock:unmatched");
            }
        };
    }
}
