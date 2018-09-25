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
package org.apache.camel.component.http4;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.http.common.DefaultHttpBinding;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for resolving reference parameters.
 *
 * @version 
 */
public class HttpReferenceParameterTest extends CamelTestSupport {

    private static final String TEST_URI_1 = "http4://localhost:8080?httpBinding=#customBinding&httpClientConfigurer=#customConfigurer&httpContext=#customContext";
    private static final String TEST_URI_2 = "http4://localhost:8081?httpBinding=#customBinding&httpClientConfigurer=#customConfigurer&httpContext=#customContext";

    private HttpEndpoint endpoint1;
    private HttpEndpoint endpoint2;

    private TestHttpBinding testBinding;
    private TestClientConfigurer testConfigurer;
    private HttpContext testHttpContext;

    @Override
    @Before
    public void setUp() throws Exception {
        this.testBinding = new TestHttpBinding();
        this.testConfigurer = new TestClientConfigurer();
        this.testHttpContext = new BasicHttpContext();
        super.setUp();
        this.endpoint1 = context.getEndpoint(TEST_URI_1, HttpEndpoint.class);
        this.endpoint2 = context.getEndpoint(TEST_URI_2, HttpEndpoint.class);
    }

    @Test
    public void testHttpBinding() {
        assertSame(testBinding, endpoint1.getHttpBinding());
        assertSame(testBinding, endpoint2.getHttpBinding());
    }

    @Test
    public void testHttpClientConfigurer() {
        assertSame(testConfigurer, endpoint1.getHttpClientConfigurer());
        assertSame(testConfigurer, endpoint2.getHttpClientConfigurer());
    }

    @Test
    public void testHttpContext() {
        assertSame(testHttpContext, endpoint1.getHttpContext());
        assertSame(testHttpContext, endpoint2.getHttpContext());
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();
        registry.bind("customBinding", testBinding);
        registry.bind("customConfigurer", testConfigurer);
        registry.bind("customContext", testHttpContext);
        return registry;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start1").to(TEST_URI_1);
                from("direct:start2").to(TEST_URI_2);
            }
        };
    }

    private static class TestHttpBinding extends DefaultHttpBinding {
    }

    private static class TestClientConfigurer implements HttpClientConfigurer {

        public void configureHttpClient(HttpClientBuilder clientBuilder) {
        }
    }
}