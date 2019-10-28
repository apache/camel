/*
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
package org.apache.camel.component.jetty.proxy;

import java.lang.reflect.UndeclaredThrowableException;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.ProxyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jetty.BaseJettyTest;
import org.apache.camel.http.common.HttpOperationFailedException;
import org.junit.Test;

public class HttpClientProxyTest extends BaseJettyTest {

    @Test
    public void testHttpClientNoProxyOk() throws Exception {
        String out = template.requestBody("direct:cool", "World", String.class);
        assertEquals("Hello World", out);
    }

    @Test
    public void testHttpClientNoProxyException() throws Exception {
        try {
            template.requestBody("direct:cool", "Kaboom", String.class);
            fail("Should have thrown exception");
        } catch (CamelExecutionException e) {
            HttpOperationFailedException cause = assertIsInstanceOf(HttpOperationFailedException.class, e.getCause());
            assertEquals(500, cause.getStatusCode());
            assertNotNull(cause.getResponseBody());
            assertTrue(cause.getResponseBody().contains("MyAppException"));
        }
    }

    @Test
    public void testHttpClientProxyOk() throws Exception {
        MyCoolService proxy = new ProxyBuilder(context).endpoint("direct:cool").build(MyCoolService.class);
        String out = proxy.hello("World");

        assertEquals("Hello World", out);
    }

    @Test
    public void testHttpClientProxyException() throws Exception {
        MyCoolService proxy = new ProxyBuilder(context).endpoint("direct:cool").build(MyCoolService.class);
        try {
            proxy.hello("Kaboom");
            fail("Should have thrown exception");
        } catch (UndeclaredThrowableException e) {
            HttpOperationFailedException cause = assertIsInstanceOf(HttpOperationFailedException.class, e.getCause());
            assertEquals(500, cause.getStatusCode());
            assertNotNull(cause.getResponseBody());
            assertTrue(cause.getResponseBody().contains("MyAppException"));
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:cool").to("http://localhost:{{port}}/myapp/myservice");

                from("jetty:http://localhost:{{port}}/myapp/myservice").bean(MyCoolServiceBean.class);
            }
        };
    }
}
