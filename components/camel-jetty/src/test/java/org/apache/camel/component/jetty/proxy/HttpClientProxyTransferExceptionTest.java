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

import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.ProxyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jetty.BaseJettyTest;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit6.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class HttpClientProxyTransferExceptionTest extends BaseJettyTest {

    @Test
    public void testHttpClientNoProxyOk() {
        String out = template.requestBody("direct:cool", "World", String.class);
        assertEquals("Hello World", out);
    }

    @Test
    public void testHttpClientNoProxyException() {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.requestBody("direct:cool", "Kaboom"),
                "Should have thrown an exception");

        MyAppException cause = assertIsInstanceOf(MyAppException.class, e.getCause());
        assertNotNull(cause);
        assertEquals("Kaboom", cause.getName());
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

        MyAppException e = assertThrows(MyAppException.class, () -> proxy.hello("Kaboom"), "Should have thrown exception");
        assertEquals("Kaboom", e.getName());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:cool").to("http://localhost:{{port}}/myapp/myservice?transferException=true");

                from("jetty:http://localhost:{{port}}/myapp/myservice?muteException=false&transferException=true")
                        .bean(MyCoolServiceBean.class);
            }
        };
    }
}
