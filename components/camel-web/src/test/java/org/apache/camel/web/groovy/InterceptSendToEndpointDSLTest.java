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

package org.apache.camel.web.groovy;

import org.junit.Ignore;
import org.junit.Test;

/**
 * 
 */
public class InterceptSendToEndpointDSLTest extends GroovyRendererTestSupport {

    @Test
    public void testInterceptSendToEndpoint() throws Exception {
        String dsl = "interceptSendToEndpoint(\"mock:foo\").to(\"mock:detour\").transform(constant(\"Bye World\"));"
            + "from(\"direct:first\").to(\"mock:bar\").to(\"mock:foo\").to(\"mock:result\")";
        assertEquals(dsl, render(dsl));
    }

    @Test
    public void testInterceptSendToEndpointDynamic() throws Exception {
        String dsl = "interceptSendToEndpoint(\"file:*\").skipSendToOriginalEndpoint().to(\"mock:detour\");"
            + "from(\"direct:first\").to(\"file://foo\").to(\"file://bar\").to(\"mock:result\")";
        assertEquals(dsl, render(dsl));
    }

    @Test
    public void testInterceptSendToEndpointInOnException() throws Exception {
        String dsl = "onException(IOException.class).handled(true).to(\"mock:io\");"
            + "interceptSendToEndpoint(\"mock:io\").skipSendToOriginalEndpoint().to(\"mock:intercepted\");"
            + "from(\"direct:start\").to(\"mock:foo\").to(\"mock:result\")";
        assertEquals(dsl, render(dsl));
    }

    @Ignore("Need to fix this test")
    @Test
    // TODO: fix this test!
    public void fixmeTestInterceptSendToIssue() throws Exception {
        String dsl = "interceptSendToEndpoint(\"direct:foo\").to(\"mock:foo\");"
            + "from(\"direct:start\").setHeader(Exchange.FILE_NAME, constant(\"hello.txt\")).to(\"direct:foo\")";
        assertEquals(dsl, render(dsl));
    }
}
