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
package org.apache.camel.component.jetty;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.http.common.HttpOperationFailedException;
import org.junit.Test;

/**
 * Unit test for HttpOperationFailedException should contain response body
 */
public class JettyResponseBodyWhenErrorTest extends BaseJettyTest {

    @Test
    public void testResponseBodyWhenError() throws Exception {
        try {
            template.requestBody("http://localhost:{{port}}/myapp/myservice", "bookid=123");
            fail("Should have thrown an exception");
        } catch (RuntimeCamelException e) {
            HttpOperationFailedException cause = assertIsInstanceOf(HttpOperationFailedException.class, e.getCause());
            assertEquals(500, cause.getStatusCode());
            String body = context.getTypeConverter().convertTo(String.class, cause.getResponseBody());
            assertTrue(body.indexOf("Damm") > -1);
            assertTrue(body.indexOf("IllegalArgumentException") > -1);
            assertNotNull(cause.getResponseHeaders());
            String type = cause.getResponseHeaders().get(Exchange.CONTENT_TYPE);
            assertTrue(type.startsWith("text/plain"));
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                errorHandler(noErrorHandler());
                from("jetty:http://localhost:{{port}}/myapp/myservice").process(new MyBookService());
            }
        };
    }

    public class MyBookService implements Processor {
        public void process(Exchange exchange) throws Exception {
            throw new IllegalArgumentException("Damm");
        }
    }

}