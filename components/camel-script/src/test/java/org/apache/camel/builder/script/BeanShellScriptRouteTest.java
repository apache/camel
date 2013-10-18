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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import static org.apache.camel.builder.script.ScriptBuilder.script;

/**
 * Unit test for a BeanSheel script
 */
public class BeanShellScriptRouteTest extends CamelTestSupport {

    @Test
    public void testSendMatchingMessage() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("mock:unmatched").expectedMessageCount(0);

        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put("foo", "bar");
        sendBody("direct:start", "hello", headers);

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

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:start").choice().
                        when(script("beanshell", "request.getHeaders().get(\"foo\").equals(\"bar\")")).to("mock:result")
                        .otherwise().to("mock:unmatched");
            }
        };
    }

}