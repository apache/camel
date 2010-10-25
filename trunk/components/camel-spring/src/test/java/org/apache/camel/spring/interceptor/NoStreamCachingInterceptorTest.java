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
package org.apache.camel.spring.interceptor;

import java.io.StringReader;
import javax.xml.transform.stream.StreamSource;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.component.mock.MockEndpoint;
import static org.apache.camel.spring.processor.SpringTestHelper.createSpringCamelContext;

/**
 * Test case for enabling stream caching through XML
 */
public class NoStreamCachingInterceptorTest extends ContextTestSupport {

    public void testNoStreamCachingInterceptorEnabled() throws Exception {
        MockEndpoint a = getMockEndpoint("mock:a");
        a.expectedMessageCount(1);

        StreamSource message = new StreamSource(new StringReader("<hello>world!</hello>"));
        template.sendBody("direct:a", message);

        assertMockEndpointsSatisfied();
        Exchange exchange = a.getExchanges().get(0);
        StreamSource stream = assertIsInstanceOf(StreamSource.class, exchange.getIn().getBody());
        assertNotNull(stream);
    }

    protected CamelContext createCamelContext() throws Exception {
        return createSpringCamelContext(this, "org/apache/camel/spring/interceptor/noStreamCachingInterceptorTest.xml");
    }

}