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
package org.apache.camel.catalog;

import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.spi.EndpointUriAssembler;
import org.apache.camel.support.component.EndpointUriAssemblerSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class CustomEndpointUriAssemblerTest extends ContextTestSupport {

    @Test
    public void testCustomAssemble() throws Exception {
        EndpointUriAssembler assembler = new MyAssembler();

        Map<String, String> params = new HashMap<>();
        params.put("timerName", "foo");
        params.put("period", "123");
        params.put("repeatCount", "5");

        String uri = assembler.buildUri(context, "timer", params);
        Assertions.assertEquals("timer:foo?period=123&repeatCount=5", uri);
    }

    @Test
    public void testCustomAssembleUnsorted() throws Exception {
        EndpointUriAssembler assembler = new MyAssembler();

        Map<String, String> params = new LinkedHashMap<>();
        params.put("timerName", "foo");
        params.put("repeatCount", "5");
        params.put("period", "123");

        String uri = assembler.buildUri(context, "timer", params);
        Assertions.assertEquals("timer:foo?period=123&repeatCount=5", uri);
    }

    private class MyAssembler extends EndpointUriAssemblerSupport implements EndpointUriAssembler {

        private static final String SYNTAX = "timer:timerName";

        @Override
        public String buildUri(CamelContext camelContext, String scheme, Map<String, String> parameters) throws URISyntaxException {
            // begin from syntax
            String uri = SYNTAX;

            // TODO: optional path parameters that are missing

            // append path parameters
            uri = buildPathParameter(camelContext, SYNTAX, uri, "timerName", null, true, parameters);
            // append remainder parameters
            uri = buildQueryParameters(camelContext, uri, parameters);

            return uri;
        }

    }

}
