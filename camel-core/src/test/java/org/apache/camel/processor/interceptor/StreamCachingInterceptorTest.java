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
package org.apache.camel.processor.interceptor;

import java.io.StringReader;
import java.util.LinkedList;
import java.util.List;

import javax.xml.transform.stream.StreamSource;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.converter.stream.StreamCache;
import org.apache.camel.model.InterceptorRef;
import org.apache.camel.model.InterceptorType;
import org.apache.camel.processor.DelegateProcessor;

public class StreamCachingInterceptorTest extends ContextTestSupport {

    private MockEndpoint a;
    private MockEndpoint b;

    public void testConvertStreamSourceWithRouteBuilderStreamCaching() throws Exception {
        a.expectedMessageCount(1);

        StreamSource message = new StreamSource(new StringReader("<hello>world!</hello>"));
        template.sendBody("direct:a", message);

        assertMockEndpointsSatisifed();
        assertTrue(a.assertExchangeReceived(0).getIn().getBody() instanceof StreamCache);
    }

    public void testConvertStreamSourceWithRouteOnlyStreamCaching() throws Exception {
        b.expectedMessageCount(1);

        StreamSource message = new StreamSource(new StringReader("<hello>world!</hello>"));
        template.sendBody("direct:b", message);

        assertMockEndpointsSatisifed();
        assertTrue(b.assertExchangeReceived(0).getIn().getBody() instanceof StreamCache);
    }

    public void testIgnoreAlreadyRereadable() throws Exception {
        a.expectedMessageCount(1);

        template.sendBody("direct:a", "<hello>world!</hello>");

        assertMockEndpointsSatisifed();
        assertTrue(a.assertExchangeReceived(0).getIn().getBody() instanceof String);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        a = getMockEndpoint("mock:a");
        b = getMockEndpoint("mock:b");
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            public void configure() {
                //Stream caching for a single route...
                from("direct:a").streamCaching().to("mock:a");

                //... or for all the following routes in this builder
                streamCaching();
                from("direct:b").to("mock:b");
            }
        };
    }

    public void testNoStreamCaching() throws Exception {
        List<InterceptorType> interceptors = new LinkedList<InterceptorType>();
        InterceptorRef streamCache = new InterceptorRef(new StreamCachingInterceptor());
        interceptors.add(streamCache);
        interceptors.add(new InterceptorRef(new DelegateProcessor()));
        StreamCachingInterceptor.noStreamCaching(interceptors);
        assertEquals(1, interceptors.size());
        assertFalse(interceptors.contains(streamCache));
    }
}
