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
package org.apache.camel.http.common;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.UnsafeUriCharactersEncoder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CamelServletTest {

    @Test
    public void testDuplicatedServletPath() throws URISyntaxException {
        CamelServlet camelServlet = new CamelServlet();

        HttpCommonEndpoint httpCommonEndpoint = new HttpCommonEndpoint() {

            @Override
            public Producer createProducer() {
                return null;
            }

            @Override
            public Consumer createConsumer(Processor processor) {
                return null;
            }
        };

        DefaultCamelContext dc = new DefaultCamelContext();

        httpCommonEndpoint.setEndpointUriIfNotSpecified("rest:post://camel.apache.org");
        httpCommonEndpoint.setHttpUri(URISupport.createRemainingURI(
                new URI(UnsafeUriCharactersEncoder.encodeHttpURI("servlet:/camel.apache.org?httpMethodRestrict=GET")),
                new LinkedHashMap<>()));
        httpCommonEndpoint.setCamelContext(dc);

        HttpConsumer httpConsumer1 = new HttpConsumer(httpCommonEndpoint, null);
        HttpConsumer httpConsumer2 = new HttpConsumer(httpCommonEndpoint, null);

        camelServlet.connect(httpConsumer1);
        IllegalStateException illegalStateException
                = assertThrows(IllegalStateException.class, () -> camelServlet.connect(httpConsumer2));
        assertEquals("Duplicate request path for rest:post://camel.apache.org",
                illegalStateException.getMessage());
    }
}
