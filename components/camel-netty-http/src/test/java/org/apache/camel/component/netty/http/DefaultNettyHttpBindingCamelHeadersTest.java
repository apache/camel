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
package org.apache.camel.component.netty.http;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DefaultNettyHttpBindingCamelHeadersTest {

    @Test
    public void testCopyCamelHeadersInAnyCase() {
        CamelContext context = new DefaultCamelContext();
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader("CamelFoo", "1");
        exchange.getIn().setHeader("camelBar", "2");
        exchange.getIn().setHeader("CAMELBAZ", "3");
        exchange.getIn().setHeader("CaMeLqux", "4");
        exchange.getIn().setHeader("foo", "5");

        Map<String, Object> headers = new HashMap<>();
        new DefaultNettyHttpBinding().copyCamelHeaders(headers, exchange);

        assertEquals(Map.of("CamelFoo", "1", "camelBar", "2", "CAMELBAZ", "3", "CaMeLqux", "4"), headers);
    }

    @Test
    public void testContentTypeInAnyCaseIsNotUrlDecoded() {
        NettyHttpConfiguration configuration = new NettyHttpConfiguration();
        configuration.setUrlDecodeHeaders(true);
        DefaultNettyHttpBinding binding = new DefaultNettyHttpBinding();

        assertEquals("text/plain; a=b%20c",
                binding.shouldUrlDecodeHeader(configuration, "content-type", "text/plain; a=b%20c", StandardCharsets.UTF_8));
        assertEquals("b c", binding.shouldUrlDecodeHeader(configuration, "foo", "b%20c", StandardCharsets.UTF_8));
    }
}
