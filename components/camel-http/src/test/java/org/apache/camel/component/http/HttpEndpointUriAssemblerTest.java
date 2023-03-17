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
package org.apache.camel.component.http;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.EndpointUriFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HttpEndpointUriAssemblerTest {

    @Test
    public void testHttpAssembler() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("httpUri", "www.google.com");
        params.put("proxyAuthHost", "myotherproxy");
        params.put("proxyAuthPort", 2345);
        params.put("proxyAuthUsername", "usr");
        params.put("proxyAuthPassword", "pwd");

        // should find the source code generated assembler via classpath
        try (CamelContext context = new DefaultCamelContext()) {
            context.start();

            EndpointUriFactory assembler = context.getCamelContextExtension().getEndpointUriFactory("https");

            assertNotNull(assembler);
            assertTrue(assembler instanceof HttpEndpointUriFactory);

            String uri = assembler.buildUri("https", params);
            assertNotNull(uri);
            assertEquals(
                    "https://www.google.com?proxyAuthHost=myotherproxy&proxyAuthPassword=RAW(pwd)&proxyAuthPort=2345&proxyAuthUsername=RAW(usr)",
                    uri);

        }
    }
}
